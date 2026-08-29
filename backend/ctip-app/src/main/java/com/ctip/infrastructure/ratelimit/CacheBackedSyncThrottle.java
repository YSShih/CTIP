package com.ctip.infrastructure.ratelimit;

import com.ctip.application.port.CachePort;
import com.ctip.application.port.SyncThrottlePort;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * {@link SyncThrottlePort} 的快取實作(docs/spec/11-sync-bloom.md §11.6 的
 * {@code min_sync_interval_seconds})。
 *
 * <p>「上次同步時間」就是一個帶 TTL 的字串,存活時間正好等於該方案的最小間隔——
 * 間隔一過這筆紀錄就再也不能拒絕任何請求,因此逐出交給 TTL,不需要自己清掃。
 * 於是兩種後端只要一個實作:{@code RATE_LIMIT_BACKEND=memory} 時
 * {@link CachePort} 是行程內的,{@code =redis} 時是 {@code SET key ts EX interval}
 * ——Phase 16 交接單寫的 {@code SETEX} 即此。
 *
 * <p>多實例下的正確性隨後端而定:Redis 後端是全域正確的;memory 後端最壞情況是 client
 * 在每個實例上各同步一次——節流變寬,但不會產生錯誤的 Bloom(§11.6 的自我驗證是第二道防線)。
 */
public class CacheBackedSyncThrottle implements SyncThrottlePort {

    private static final String KEY_PREFIX = "sync:last:";

    private final CachePort cache;

    public CacheBackedSyncThrottle(CachePort cache) {
        this.cache = cache;
    }

    @Override
    public Optional<Instant> lastSyncAt(String subject) {
        return cache.get(KEY_PREFIX + subject).map(Instant::parse);
    }

    @Override
    public void recordSync(String subject, Instant at, Duration minInterval) {
        if (minInterval.isZero() || minInterval.isNegative()) {
            return;
        }
        cache.put(KEY_PREFIX + subject, at.toString(), minInterval);
    }
}
