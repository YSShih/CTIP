package com.ctip.domain.plan;

import java.util.Objects;

/**
 * 方案定義(docs/spec/04-data-dictionary.md 表 17):§10.6 的 14 個配額維度全部存於 plans 表,
 * <strong>不得 hard-code</strong>。本型別是該表的唯讀投影(參考資料,非聚合根)。
 *
 * <p>刻意<strong>沒有</strong>任何 TLP 相關欄位:TLP 可見度由「認證狀態 + 資料歸屬」決定,
 * 與方案完全解耦(§10.6、07 §7.7)。新增此類欄位視為規格違規。
 */
public record Plan(
        PlanId id,
        PlanCode code,
        String name,
        int tier,
        QuotaLimit requestsPerMinute,
        QuotaLimit requestsPerDay,
        int maxPageSize,
        QuotaLimit maxBatchLookup,
        int minSyncIntervalSeconds,
        boolean publicBloomEnabled,
        QuotaLimit tenantBloomCapacity,
        boolean websocketEnabled,
        QuotaLimit maxWebhooks,
        QuotaLimit maxApiKeys,
        boolean customFeedEnabled,
        QuotaLimit stixExportMaxObjects,
        QuotaLimit maxManualSubmissionsPerDay,
        QuotaLimit maxImportRowsPerFile) {

    public Plan {
        Objects.requireNonNull(id, "id 不得為 null");
        Objects.requireNonNull(code, "code 不得為 null");
        Objects.requireNonNull(name, "name 不得為 null");
        if (maxPageSize < 1) {
            throw new IllegalArgumentException("maxPageSize 必須 >= 1:" + maxPageSize);
        }
        if (minSyncIntervalSeconds < 0) {
            throw new IllegalArgumentException("minSyncIntervalSeconds 不得為負:" + minSyncIntervalSeconds);
        }
    }
}
