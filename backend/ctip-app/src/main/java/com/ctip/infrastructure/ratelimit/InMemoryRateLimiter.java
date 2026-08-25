package com.ctip.infrastructure.ratelimit;

import com.ctip.application.port.ClockPort;
import com.ctip.application.port.RateLimitKey;
import com.ctip.application.port.RateLimitResult;
import com.ctip.application.port.RateLimiterPort;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RateLimiterPort 的 Bucket4j 記憶體實作(docs/spec/10-identity-plans.md §10.7):
 * RATE_LIMIT_BACKEND=memory,僅單一實例正確;Phase 17 提供 Redis 後端。
 * 視窗以 interval refill 對齊,resetAt 取自 bucket 的重置時距。
 */
public class InMemoryRateLimiter implements RateLimiterPort {

    private final long perMinute;
    private final long perDay;
    private final ClockPort clock;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public InMemoryRateLimiter(long perMinute, long perDay, ClockPort clock) {
        this.perMinute = perMinute;
        this.perDay = perDay;
        this.clock = clock;
    }

    @Override
    public RateLimitResult tryConsume(RateLimitKey key, int tokens) {
        long limit = limitFor(key.window());
        Bucket bucket = buckets.computeIfAbsent(key.asString(), k -> newBucket(limit, key));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(tokens);
        return new RateLimitResult(
                probe.isConsumed(),
                limit,
                Math.max(0, probe.getRemainingTokens()),
                clock.now().plusNanos(probe.getNanosToWaitForReset()));
    }

    private long limitFor(RateLimitKey.Window window) {
        return switch (window) {
            case MINUTE -> perMinute;
            case DAY -> perDay;
        };
    }

    private static Bucket newBucket(long limit, RateLimitKey key) {
        Bandwidth bandwidth = Bandwidth.builder()
                .capacity(limit)
                .refillIntervally(limit, key.window().duration())
                .build();
        return Bucket.builder().addLimit(bandwidth).build();
    }
}
