package com.ctip.application.port;

import com.ctip.domain.plan.QuotaLimit;
import java.time.Instant;
import java.util.Objects;

/**
 * 一次限流判定的結果(docs/spec/10-identity-plans.md §10.7);X-RateLimit-* 標頭的資料來源。
 * {@code remaining} 在無上限時為 {@link Long#MAX_VALUE},由呈現層依 {@code limit.isUnlimited()}
 * 決定如何輸出——不得把它當成真實數字印出去。
 */
public record RateLimitResult(boolean allowed, QuotaLimit limit, long remaining, Instant resetAt) {

    public RateLimitResult {
        Objects.requireNonNull(limit, "limit 不得為 null");
        Objects.requireNonNull(resetAt, "resetAt 不得為 null");
    }

    public static RateLimitResult unlimited(Instant resetAt) {
        return new RateLimitResult(true, QuotaLimit.unlimited(), Long.MAX_VALUE, resetAt);
    }

    /** 停用(配額 0):永遠不允許,且不會隨視窗恢復。 */
    public static RateLimitResult disabled(Instant resetAt) {
        return new RateLimitResult(false, QuotaLimit.disabled(), 0, resetAt);
    }

    public long used() {
        return limit.isUnlimited() ? 0 : Math.max(0, limit.orElse(0) - remaining);
    }
}
