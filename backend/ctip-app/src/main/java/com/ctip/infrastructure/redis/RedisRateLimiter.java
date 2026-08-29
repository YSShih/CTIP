package com.ctip.infrastructure.redis;

import com.ctip.application.port.ClockPort;
import com.ctip.application.port.RateLimitKey;
import com.ctip.application.port.RateLimitResult;
import com.ctip.application.port.RateLimiterPort;
import com.ctip.domain.plan.QuotaLimit;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.api.StatefulRedisConnection;
import java.time.Duration;
import java.time.Instant;

/**
 * {@link RateLimiterPort} 的 Redis 實作(docs/spec/10-identity-plans.md §10.7:
 * {@code RATE_LIMIT_BACKEND=redis},Bucket4j + {@code bucket4j-redis})。
 * 桶的狀態存在 Redis,因此<strong>多個 app 實例共用同一個配額</strong>
 * ——DoD M2-09 以兩個實例驗證(一個實例耗盡後另一個也被拒)。
 *
 * <p><strong>Redis 鍵多帶一段容量</strong>(§10.7 的鍵格式偏離,ADR 0026):bucket4j 的
 * proxy 把 {@code BucketConfiguration} 一併存進 Redis,建立後不會因為呼叫端傳了不同的限額而更新
 * ——方案降級時舊桶會繼續用舊的(較寬的)容量直到過期,那是 fail-open。
 * 把容量放進鍵等於「限額改變即換一個桶」,與 {@code InMemoryRateLimiter}「限額改變即重建」同語意。
 * 舊鍵由 TTL 自行消失。
 *
 * <p>Redis 故障時<strong>不</strong>降級放行:限流是安全機制,連不上就讓例外往上冒
 * (由 {@code TraceIdFilter} 的錯誤網寫成 500)。靜默放行會讓「Redis 掛了」變成
 * 「限流不存在」,那正是攻擊者要的狀態(§0.4 安全性優先)。
 */
public class RedisRateLimiter implements RateLimiterPort {

    /**
     * 桶的存活時間 = 補滿所需時間再加這段餘裕。分鐘桶約 1 分鐘後消失、日桶約一天,
     * 不必為每個匿名 IP 永久保留狀態(記憶體實作的清掃在此由 Redis 的 TTL 承擔)。
     */
    private static final Duration EXPIRY_SLACK = Duration.ofMinutes(1);

    private final ProxyManager<String> proxyManager;
    private final ClockPort clock;

    public RedisRateLimiter(StatefulRedisConnection<String, byte[]> connection, ClockPort clock) {
        this.proxyManager = LettuceBasedProxyManager.builderFor(connection)
                .withExpirationStrategy(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(EXPIRY_SLACK))
                .build();
        this.clock = clock;
    }

    @Override
    public RateLimitResult tryConsume(RateLimitKey key, int tokens, QuotaLimit limit) {
        Instant now = clock.now();
        if (limit.isUnlimited()) {
            return RateLimitResult.unlimited(windowEnd(now, key));
        }
        if (limit.isDisabled()) {
            return RateLimitResult.disabled(windowEnd(now, key));
        }
        ConsumptionProbe probe = bucketFor(key, limit).tryConsumeAndReturnRemaining(tokens);
        return new RateLimitResult(
                probe.isConsumed(),
                limit,
                Math.max(0, probe.getRemainingTokens()),
                now.plusNanos(probe.getNanosToWaitForReset()));
    }

    @Override
    public RateLimitResult peek(RateLimitKey key, QuotaLimit limit) {
        Instant now = clock.now();
        if (limit.isUnlimited()) {
            return RateLimitResult.unlimited(windowEnd(now, key));
        }
        if (limit.isDisabled()) {
            return RateLimitResult.disabled(windowEnd(now, key));
        }
        long remaining = bucketFor(key, limit).getAvailableTokens();
        return new RateLimitResult(remaining > 0, limit, remaining, windowEnd(now, key));
    }

    @Override
    public void refund(RateLimitKey key, int tokens, QuotaLimit limit) {
        if (limit.isUnlimited() || limit.isDisabled()) {
            return;
        }
        // addTokens 會被容量夾住,不會歸還出比容量更多的 token
        bucketFor(key, limit).addTokens(tokens);
    }

    private Bucket bucketFor(RateLimitKey key, QuotaLimit limit) {
        long capacity = limit.orElse(0);
        BucketConfiguration configuration =
                configurationOf(capacity, key.window().duration());
        return proxyManager.builder().build(redisKey(key, capacity), () -> configuration);
    }

    private static String redisKey(RateLimitKey key, long capacity) {
        return key.asString() + ":" + capacity;
    }

    private static BucketConfiguration configurationOf(long capacity, Duration window) {
        return BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(capacity)
                        .refillIntervally(capacity, window)
                        .build())
                .build();
    }

    private static Instant windowEnd(Instant now, RateLimitKey key) {
        return now.plus(key.window().duration());
    }
}
