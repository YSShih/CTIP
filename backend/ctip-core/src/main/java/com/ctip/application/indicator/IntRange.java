package com.ctip.application.indicator;

/** 閉區間整數範圍條件;端點 null 表示該側不設限。 */
public record IntRange(Integer min, Integer max) {

    private static final IntRange UNBOUNDED = new IntRange(null, null);

    public static IntRange unbounded() {
        return UNBOUNDED;
    }

    public static IntRange of(Integer min, Integer max) {
        return min == null && max == null ? UNBOUNDED : new IntRange(min, max);
    }

    public boolean isUnbounded() {
        return min == null && max == null;
    }
}
