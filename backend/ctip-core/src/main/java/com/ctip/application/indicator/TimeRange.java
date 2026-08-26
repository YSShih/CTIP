package com.ctip.application.indicator;

import java.time.Instant;

/** 閉區間時間範圍條件;端點 null 表示該側不設限。 */
public record TimeRange(Instant from, Instant to) {

    private static final TimeRange UNBOUNDED = new TimeRange(null, null);

    public static TimeRange unbounded() {
        return UNBOUNDED;
    }

    public static TimeRange of(Instant from, Instant to) {
        return from == null && to == null ? UNBOUNDED : new TimeRange(from, to);
    }

    public boolean isUnbounded() {
        return from == null && to == null;
    }
}
