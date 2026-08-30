package com.ctip.config;

import com.ctip.application.port.CachePort;
import com.ctip.application.port.ClockPort;
import com.ctip.application.port.RateLimiterPort;
import com.ctip.infrastructure.redis.RedisCacheAdapter;
import com.ctip.infrastructure.redis.RedisRateLimiter;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.metrics.MicrometerCommandLatencyRecorder;
import io.lettuce.core.metrics.MicrometerOptions;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.data.redis.autoconfigure.ClientResourcesBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * {@code RATE_LIMIT_BACKEND=redis} 時的後端裝配(docs/spec/10-identity-plans.md §10.7、
 * 06 §6.2.2 的 [P17])。記憶體後端在 {@link RateLimitConfig}(兩者以同一個屬性互斥)。
 *
 * <p>連線參數只有一個來源:{@code spring.data.redis.*}(§5.7 的 REDIS_HOST/PORT/PASSWORD)。
 * Bucket4j 需要的是原生 Lettuce 連線,因此向 Boot 已建好的 {@link LettuceConnectionFactory}
 * 借用同一個 {@link RedisClient},而不是自己讀一次環境變數再建第二套設定。
 *
 * <p>連線在啟動時建立:Redis 連不上就<strong>啟動失敗</strong>。這是刻意的——限流是安全機制,
 * 「後端不可用就悄悄改用記憶體」等於多實例下的限流形同虛設(§0.4 安全性優先)。
 * 容器的 restart policy 會在 Redis 就緒後把 app 拉起來。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "ctip.rate-limit.backend", havingValue = "redis")
public class RedisConfig {

    /** 鍵是字串(§10.7 的 {@code ratelimit:...}),值是 bucket4j 的二進位快照。 */
    @Bean(destroyMethod = "close")
    StatefulRedisConnection<String, byte[]> ctipRedisConnection(RedisConnectionFactory connectionFactory) {
        if (!(connectionFactory instanceof LettuceConnectionFactory lettuce)) {
            throw new IllegalStateException(
                    "RATE_LIMIT_BACKEND=redis 需要 Lettuce 連線工廠,實際為 " + connectionFactory.getClass());
        }
        if (!(lettuce.getNativeClient() instanceof RedisClient client)) {
            throw new IllegalStateException("RATE_LIMIT_BACKEND=redis 尚不支援 Redis Cluster 連線");
        }
        return client.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
    }

    @Bean
    RateLimiterPort rateLimiterPort(StatefulRedisConnection<String, byte[]> connection, ClockPort clock) {
        return new RedisRateLimiter(connection, clock);
    }

    @Bean
    CachePort cachePort(StringRedisTemplate redis) {
        return new RedisCacheAdapter(redis);
    }

    /**
     * {@code lettuce.*} 指標(13 §13.6)。Boot 4 的 {@code spring-boot-data-redis} 沒有
     * metrics autoconfig,要在 Lettuce 自己的 {@code ClientResources} 上掛延遲記錄器;
     * 而本專案的原生連線是向 Boot 建好的 {@code LettuceConnectionFactory} 借的,
     * 因此只能在 Boot 建立 {@code ClientResources} 的當下就掛上去。
     */
    @Bean
    ClientResourcesBuilderCustomizer lettuceMetricsCustomizer(MeterRegistry registry) {
        return builder -> builder.commandLatencyRecorder(
                new MicrometerCommandLatencyRecorder(registry, MicrometerOptions.create()));
    }
}
