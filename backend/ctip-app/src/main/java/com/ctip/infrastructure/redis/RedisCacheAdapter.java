package com.ctip.infrastructure.redis;

import com.ctip.application.port.CachePort;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * {@link CachePort} 的 Redis 實作(docs/spec/06-tech-stack.md §6.5、10 §10.7 的分散式後端)。
 *
 * <p>只用 {@code GET} / {@code SET key value EX ttl} / {@code DEL} 三個指令——
 * §6.5 要求「Redis → Valkey 的替換只需改 infrastructure 實作與 image 名稱」,
 * 而 Valkey 與 Redis 在這三個指令上完全相容,連本類別都不必改,只需換 image。
 *
 * <p><strong>快取失效不得使請求失敗</strong>:Redis 連不上時退化為「每次都重新載入」
 * ——{@link CachePort} 的契約就是允許回 empty。因此這裡捕捉 {@link DataAccessException}
 * (Spring 把 Lettuce 的連線／逾時例外一律包成它)並記 WARN,而不是讓例外往上冒到 filter。
 * 刻意<strong>不</strong>捕捉其他 RuntimeException:那是程式錯誤,不該被靜默。
 */
public class RedisCacheAdapter implements CachePort {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheAdapter.class);

    private final StringRedisTemplate redis;

    public RedisCacheAdapter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Optional<String> get(String key) {
        try {
            return Optional.ofNullable(redis.opsForValue().get(key));
        } catch (DataAccessException e) {
            log.warn("Redis 快取讀取失敗,改為重新載入:key={}", key, e);
            return Optional.empty();
        }
    }

    @Override
    public void put(String key, String value, Duration ttl) {
        try {
            redis.opsForValue().set(key, value, ttl);
        } catch (DataAccessException e) {
            log.warn("Redis 快取寫入失敗,本次不快取:key={}", key, e);
        }
    }

    @Override
    public void evict(String key) {
        try {
            redis.delete(key);
        } catch (DataAccessException e) {
            // 失效失敗會讓其他實例最多多用 TTL 這段時間的舊值;TTL 是最後防線,故只記錄不重試
            log.warn("Redis 快取失效失敗,舊值將於 TTL 到期後消失:key={}", key, e);
        }
    }
}
