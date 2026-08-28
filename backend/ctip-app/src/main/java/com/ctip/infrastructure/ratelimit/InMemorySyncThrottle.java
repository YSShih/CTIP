package com.ctip.infrastructure.ratelimit;

import com.ctip.application.port.ClockPort;
import com.ctip.application.port.SyncThrottlePort;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * {@link SyncThrottlePort} 的記憶體實作(docs/spec/11-sync-bloom.md §11.6)。
 *
 * <p>與 {@link InMemoryRateLimiter} 同一個定位:僅單一實例正確,Phase 17 隨 Redis 換掉
 * (屆時是一個 {@code SETEX subject interval timestamp},TTL 讓逐出自動發生)。
 * 多實例下最壞情況是 client 在每個實例上各同步一次——節流變寬,但不會產生錯誤的 Bloom。
 *
 * <p>鍵含匿名 client IP,因此<strong>必須</strong>逐出:過期的項目已經不能拒絕任何請求,
 * 留著只會讓 map 隨流量無上限成長(與 {@link InMemoryRateLimiter} 同一條記憶體洩漏路徑,ADR 0015)。
 */
public class InMemorySyncThrottle implements SyncThrottlePort {

    /** 超過此數量才觸發清掃。 */
    private static final int SWEEP_THRESHOLD = 10_000;

    /** 清掃是 O(n),再以時間節流一次,避免每次記帳都掃整個 map。 */
    private static final Duration SWEEP_INTERVAL = Duration.ofMinutes(10);

    private final ClockPort clock;
    private final Map<String, Entry> lastSync = new ConcurrentHashMap<>();
    private final AtomicReference<Instant> lastSweep = new AtomicReference<>(Instant.EPOCH);

    public InMemorySyncThrottle(ClockPort clock) {
        this.clock = clock;
    }

    /** {@code expiresAt} = 記帳時間 + 該方案的最小間隔:到期後這筆紀錄再也不會拒絕任何請求。 */
    private record Entry(Instant at, Instant expiresAt) {}

    @Override
    public Optional<Instant> lastSyncAt(String subject) {
        Entry entry = lastSync.get(subject);
        if (entry == null) {
            return Optional.empty();
        }
        if (!clock.now().isBefore(entry.expiresAt())) {
            lastSync.remove(subject, entry);
            return Optional.empty();
        }
        return Optional.of(entry.at());
    }

    @Override
    public void recordSync(String subject, Instant at, Duration minInterval) {
        lastSync.put(subject, new Entry(at, at.plus(minInterval)));
        sweepExpired();
    }

    private void sweepExpired() {
        if (lastSync.size() <= SWEEP_THRESHOLD) {
            return;
        }
        Instant now = clock.now();
        Instant previous = lastSweep.get();
        if (now.isBefore(previous.plus(SWEEP_INTERVAL)) || !lastSweep.compareAndSet(previous, now)) {
            return;
        }
        lastSync.values().removeIf(entry -> !now.isBefore(entry.expiresAt()));
    }
}
