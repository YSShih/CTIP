package com.ctip.infrastructure.cache;

import com.ctip.application.port.CachePort;
import com.ctip.application.port.ClockPort;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * {@link CachePort} 的行程內實作({@code RATE_LIMIT_BACKEND=memory},即 mvp 環境沒有 Redis 可用時)。
 *
 * <p><strong>僅單一實例正確</strong>:{@link #evict} 只清掉本行程的項目,另一個實例要等 TTL 到期。
 * 這與 {@code InMemoryRateLimiter} 是同一個定位,亦是 §5.7 啟動守衛
 * 「{@code ENVIRONMENT != mvp} 且後端為 memory 即 WARN」的原因。
 *
 * <p>過期項目必須逐出:鍵含租戶／角色以外還有 client 主體(同步節流),不清掃就是一條
 * 隨流量成長、永不回收的記憶體洩漏路徑(ADR 0015 同一類問題)。
 */
public class InMemoryCache implements CachePort {

    /** 超過此數量才觸發清掃。 */
    private static final int SWEEP_THRESHOLD = 10_000;

    /** 清掃是 O(n),再以時間節流一次,避免每次寫入都掃整個 map。 */
    private static final Duration SWEEP_INTERVAL = Duration.ofMinutes(10);

    private final ClockPort clock;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final AtomicReference<Instant> lastSweep = new AtomicReference<>(Instant.EPOCH);

    public InMemoryCache(ClockPort clock) {
        this.clock = clock;
    }

    private record Entry(String value, Instant expiresAt) {}

    @Override
    public Optional<String> get(String key) {
        Entry entry = entries.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (!clock.now().isBefore(entry.expiresAt())) {
            entries.remove(key, entry);
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }

    @Override
    public void put(String key, String value, Duration ttl) {
        entries.put(key, new Entry(value, clock.now().plus(ttl)));
        sweepExpired();
    }

    @Override
    public void evict(String key) {
        entries.remove(key);
    }

    private void sweepExpired() {
        if (entries.size() <= SWEEP_THRESHOLD) {
            return;
        }
        Instant now = clock.now();
        Instant previous = lastSweep.get();
        if (now.isBefore(previous.plus(SWEEP_INTERVAL)) || !lastSweep.compareAndSet(previous, now)) {
            return;
        }
        entries.values().removeIf(entry -> !now.isBefore(entry.expiresAt()));
    }
}
