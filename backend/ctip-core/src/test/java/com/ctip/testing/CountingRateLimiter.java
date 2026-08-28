package com.ctip.testing;

import com.ctip.application.port.ClockPort;
import com.ctip.application.port.RateLimitKey;
import com.ctip.application.port.RateLimitResult;
import com.ctip.application.port.RateLimiterPort;
import com.ctip.domain.plan.QuotaLimit;
import java.util.HashMap;
import java.util.Map;

/** 測試用限流器:單純計數,語意與 InMemoryRateLimiter 對齊(無限制放行、0 一律拒絕)。 */
public final class CountingRateLimiter implements RateLimiterPort {

    private final Map<String, Long> consumed = new HashMap<>();
    private final ClockPort clock;

    public CountingRateLimiter(ClockPort clock) {
        this.clock = clock;
    }

    @Override
    public RateLimitResult tryConsume(RateLimitKey key, int tokens, QuotaLimit limit) {
        if (limit.isUnlimited()) {
            return RateLimitResult.unlimited(resetAt(key));
        }
        if (limit.isDisabled()) {
            return RateLimitResult.disabled(resetAt(key));
        }
        long used = consumed.getOrDefault(key.asString(), 0L);
        if (limit.isExceededBy(used + tokens)) {
            return new RateLimitResult(false, limit, Math.max(0, limit.orElse(0) - used), resetAt(key));
        }
        consumed.put(key.asString(), used + tokens);
        return new RateLimitResult(true, limit, limit.orElse(0) - used - tokens, resetAt(key));
    }

    @Override
    public RateLimitResult peek(RateLimitKey key, QuotaLimit limit) {
        if (limit.isUnlimited()) {
            return RateLimitResult.unlimited(resetAt(key));
        }
        long used = consumed.getOrDefault(key.asString(), 0L);
        return new RateLimitResult(
                !limit.isExceededBy(used + 1), limit, Math.max(0, limit.orElse(0) - used), resetAt(key));
    }

    private java.time.Instant resetAt(RateLimitKey key) {
        return clock.now().plus(key.window().duration());
    }
}
