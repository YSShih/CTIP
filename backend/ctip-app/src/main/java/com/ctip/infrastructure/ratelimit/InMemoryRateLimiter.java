package com.ctip.infrastructure.ratelimit;

import com.ctip.application.port.ClockPort;
import com.ctip.application.port.RateLimitKey;
import com.ctip.application.port.RateLimitResult;
import com.ctip.application.port.RateLimiterPort;
import com.ctip.domain.plan.QuotaLimit;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * RateLimiterPort 的 Bucket4j 記憶體實作(docs/spec/10-identity-plans.md §10.7):
 * RATE_LIMIT_BACKEND=memory,僅單一實例正確;Phase 17 提供 Redis 後端。
 * 視窗以 interval refill 對齊,resetAt 取自 bucket 的重置時距。
 *
 * <p>限額隨呼叫傳入(Phase 14):同一把鍵在方案變更後限額會不同,故 bucket 記住建立時的限額,
 * 限額改變即重建——否則升級方案的租戶會被舊 bucket 的容量綁住到視窗結束。
 *
 * <p>bucket map 會逐出:鍵含 client IP(IPv6 取 /64),長時間執行下不逐出就是一條隨流量成長、
 * 永不回收的記憶體洩漏路徑。滿桶(未被消耗過或已回滿)且閒置超過視窗長度的項目可安全移除——
 * 重建出來的新 bucket 與被移除的那個狀態相同,因此逐出不會放寬任何配額(ADR 0015)。
 */
public class InMemoryRateLimiter implements RateLimiterPort {

    /** 超過此數量才觸發清掃。 */
    private static final int SWEEP_THRESHOLD = 10_000;

    /** 清掃是 O(n),再以時間節流一次,避免高流量下每個請求都掃整個 map。 */
    private static final Duration SWEEP_INTERVAL = Duration.ofMinutes(10);

    private final ClockPort clock;
    private final Map<String, Entry> buckets = new ConcurrentHashMap<>();
    private final AtomicReference<Instant> lastSweep = new AtomicReference<>(Instant.EPOCH);

    public InMemoryRateLimiter(ClockPort clock) {
        this.clock = clock;
    }

    private record Entry(Bucket bucket, long capacity, AtomicReference<Instant> lastUsed) {}

    @Override
    public RateLimitResult tryConsume(RateLimitKey key, int tokens, QuotaLimit limit) {
        Instant now = clock.now();
        if (limit.isUnlimited()) {
            return RateLimitResult.unlimited(windowEnd(now, key));
        }
        if (limit.isDisabled()) {
            return RateLimitResult.disabled(windowEnd(now, key));
        }
        Entry entry = entryFor(key, limit, now);
        ConsumptionProbe probe = entry.bucket().tryConsumeAndReturnRemaining(tokens);
        evictIdleFullBuckets(now);
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
        Entry entry = entryFor(key, limit, now);
        long remaining = entry.bucket().getAvailableTokens();
        return new RateLimitResult(remaining > 0, limit, remaining, windowEnd(now, key));
    }

    @Override
    public void refund(RateLimitKey key, int tokens, QuotaLimit limit) {
        if (limit.isUnlimited() || limit.isDisabled()) {
            return;
        }
        // 只歸還「這個容量的桶」;限額在期間內變動過就沒有可歸還的對象,重建出來的新桶本來就是滿的
        Entry entry = buckets.get(key.asString());
        if (entry != null && entry.capacity() == limit.orElse(0)) {
            entry.bucket().addTokens(tokens);
        }
    }

    /** 限額變動時重建 bucket——舊 bucket 的容量是建立當下的方案值,不會自己跟著調整。 */
    private Entry entryFor(RateLimitKey key, QuotaLimit limit, Instant now) {
        long capacity = limit.orElse(0);
        Entry entry = buckets.compute(
                key.asString(),
                (k, existing) -> existing != null && existing.capacity() == capacity
                        ? existing
                        : new Entry(newBucket(capacity, key), capacity, new AtomicReference<>(now)));
        entry.lastUsed().set(now);
        return entry;
    }

    private static Instant windowEnd(Instant now, RateLimitKey key) {
        return now.plus(key.window().duration());
    }

    /**
     * 只逐出「已回滿且閒置超過一天」的 bucket。回滿代表它不再限制任何人,
     * 移除後重建的 bucket 狀態相同——配額語意不變,只是不再無限成長。
     */
    private void evictIdleFullBuckets(Instant now) {
        if (buckets.size() <= SWEEP_THRESHOLD) {
            return;
        }
        Instant previous = lastSweep.get();
        if (now.isBefore(previous.plus(SWEEP_INTERVAL)) || !lastSweep.compareAndSet(previous, now)) {
            return;
        }
        Instant idleBefore = now.minus(RateLimitKey.Window.DAY.duration());
        buckets.values()
                .removeIf(entry -> entry.lastUsed().get().isBefore(idleBefore)
                        && entry.bucket().getAvailableTokens() >= entry.capacity());
    }

    private static Bucket newBucket(long limit, RateLimitKey key) {
        Bandwidth bandwidth = Bandwidth.builder()
                .capacity(limit)
                .refillIntervally(limit, key.window().duration())
                .build();
        return Bucket.builder().addLimit(bandwidth).build();
    }
}
