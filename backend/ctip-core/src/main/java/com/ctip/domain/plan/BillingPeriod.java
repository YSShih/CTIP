package com.ctip.domain.plan;

import java.time.Instant;
import java.util.Objects;

/**
 * 訂閱的計費區間(docs/spec/02-ddd-model.md §2.2 Subscription 的值物件)。
 * 不變量 B2:{@code end} 為 null(無期限)或嚴格晚於 {@code start}。
 */
public record BillingPeriod(Instant start, Instant end) {

    public BillingPeriod {
        Objects.requireNonNull(start, "start 不得為 null");
        if (end != null && !end.isAfter(start)) {
            throw new IllegalArgumentException("currentPeriodEnd 必須晚於 currentPeriodStart(不變量 B2)");
        }
    }

    public static BillingPeriod openEnded(Instant start) {
        return new BillingPeriod(start, null);
    }

    /** 無期限者永不到期。 */
    public boolean hasEndedBy(Instant now) {
        return end != null && !end.isAfter(now);
    }
}
