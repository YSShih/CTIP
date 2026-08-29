package com.ctip.domain.plan;

/**
 * 限流維度 5 的端點類別(docs/spec/10-identity-plans.md §10.7)。
 *
 * <p>{@code plans} 表只有 {@code requests_per_minute} / {@code requests_per_day} 各一組,
 * 三類各自的數值規格從未定義;定調為<strong>總配額的比例</strong>(ADR 0020):
 * read 100%、write 20%、heavy 5%(取整,至少 1)。比例是常數,不進 plans 表——
 * 每個方案多開六個欄位只是把同一組比例抄四遍,而比例保證分類上限恆低於總上限。
 */
public enum EndpointClass {

    /** GET 與查詢(§10.7 明文 "read(GET/查詢)");POST 的搜尋／批次查詢屬此類。 */
    READ(100),

    /** 改變狀態的請求(POST/PATCH/PUT/DELETE)。 */
    WRITE(20),

    /** Bloom 下載、STIX bundle、匯入——單次成本高出數個量級。 */
    HEAVY(5);

    private final int percent;

    EndpointClass(int percent) {
        this.percent = percent;
    }

    /** 鍵中的類別名(§10.7 的 {@code ratelimit:{scope}:{endpointClass}:{window}})。 */
    public String keySegment() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * 本類別在該方案下的上限。
     *
     * <p>無上限的方案(ENTERPRISE 的 {@code requests_per_day} 為 null)按比例仍是無上限——
     * 比例套在「沒有數字」上得不到數字,而合約上該方案本來就不設日上限。
     * 停用(0)維持停用。正整數一律至少 1:比例取整後為 0 會把「有配額」變成「完全不能用」。
     */
    public QuotaLimit shareOf(QuotaLimit total) {
        if (total.isUnlimited() || total.isDisabled()) {
            return total;
        }
        long value = total.orElse(0) * percent / 100;
        return QuotaLimit.of(Math.max(1, value));
    }
}
