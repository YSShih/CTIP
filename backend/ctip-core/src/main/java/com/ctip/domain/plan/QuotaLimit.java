package com.ctip.domain.plan;

/**
 * 一個配額值(docs/spec/10-identity-plans.md §10.6;ADR 0019 定調)。
 *
 * <p>plans 表的欄位有三種語意,少一種就會把「停用」誤讀成「無限制」:
 * <ul>
 *   <li>{@code 0} = <strong>停用</strong>——該方案沒有這項能力(例:ANONYMOUS 的 max_api_keys)</li>
 *   <li>{@code null} = <strong>無限制</strong>(例:ENTERPRISE 的 requests_per_day「依合約」)</li>
 *   <li>正整數 = 上限本身</li>
 * </ul>
 *
 * <p>因此不能用 {@code long} 原始型別承載:{@code 0} 與「無限」在原始型別上無法同時表達,
 * 而 {@code X-RateLimit-Limit} 必須出現在所有回應(§10.7)。
 */
public record QuotaLimit(Long value) {

    private static final QuotaLimit UNLIMITED = new QuotaLimit(null);
    private static final QuotaLimit DISABLED = new QuotaLimit(0L);

    public QuotaLimit {
        if (value != null && value < 0) {
            throw new IllegalArgumentException("配額值不得為負:" + value);
        }
    }

    public static QuotaLimit unlimited() {
        return UNLIMITED;
    }

    public static QuotaLimit disabled() {
        return DISABLED;
    }

    /** null 一律解為「無限制」——欄位可為 NULL 的語意就是無上限。 */
    public static QuotaLimit of(Integer nullableValue) {
        return nullableValue == null ? UNLIMITED : new QuotaLimit(nullableValue.longValue());
    }

    public static QuotaLimit of(Long nullableValue) {
        return nullableValue == null ? UNLIMITED : new QuotaLimit(nullableValue);
    }

    public boolean isUnlimited() {
        return value == null;
    }

    /** 停用:該方案沒有這項能力,等待不會恢復(§9.7 → 403 PLAN_LIMIT_EXCEEDED)。 */
    public boolean isDisabled() {
        return value != null && value == 0L;
    }

    /** 以 amount 是否超過上限判定;無限制恆為 false。 */
    public boolean isExceededBy(long amount) {
        return !isUnlimited() && amount > value;
    }

    /** 需要具體數值的呼叫端(分頁夾值等);無限制時回傳 fallback。 */
    public long orElse(long fallback) {
        return isUnlimited() ? fallback : value;
    }

    /** 夾到上限;無限制時原樣回傳。 */
    public int clamp(int requested) {
        return isUnlimited() ? requested : (int) Math.min(requested, value);
    }
}
