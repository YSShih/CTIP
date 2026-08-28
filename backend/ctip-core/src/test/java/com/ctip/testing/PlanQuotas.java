package com.ctip.testing;

import com.ctip.domain.plan.Plan;
import com.ctip.domain.plan.QuotaLimit;

/** 測試用的單欄配額改寫(方案有 18 個成員,逐測試重打一次沒有意義)。 */
public final class PlanQuotas {

    private PlanQuotas() {}

    public static Plan manualSubmissionsPerDay(Plan plan, long value) {
        return new Plan(
                plan.id(),
                plan.code(),
                plan.name(),
                plan.tier(),
                plan.requestsPerMinute(),
                plan.requestsPerDay(),
                plan.maxPageSize(),
                plan.maxBatchLookup(),
                plan.minSyncIntervalSeconds(),
                plan.publicBloomEnabled(),
                plan.tenantBloomCapacity(),
                plan.websocketEnabled(),
                plan.maxWebhooks(),
                plan.maxApiKeys(),
                plan.customFeedEnabled(),
                plan.stixExportMaxObjects(),
                QuotaLimit.of(value),
                plan.maxImportRowsPerFile());
    }
}
