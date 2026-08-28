package com.ctip.infrastructure.persistence;

import com.ctip.domain.plan.Plan;
import com.ctip.domain.plan.PlanCode;
import com.ctip.domain.plan.PlanId;
import com.ctip.domain.plan.QuotaLimit;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

/**
 * Plan ↔ plans 列。可為 null 的欄位一律經 {@link QuotaLimit#of}:
 * null = 無限制、0 = 停用,兩者不可互相塌陷(ADR 0019)。
 */
@Mapper(componentModel = "spring")
interface PlanMapper {

    default Plan toDomain(PlanEntity e) {
        return new Plan(
                new PlanId(e.id),
                PlanCode.valueOf(e.code),
                e.name,
                e.tier,
                QuotaLimit.of(e.requestsPerMinute),
                QuotaLimit.of(e.requestsPerDay),
                e.maxPageSize,
                QuotaLimit.of(e.maxBatchLookup),
                e.minSyncIntervalSeconds,
                e.publicBloomEnabled,
                QuotaLimit.of(e.tenantBloomCapacity),
                e.websocketEnabled,
                QuotaLimit.of(e.maxWebhooks),
                QuotaLimit.of(e.maxApiKeys),
                e.customFeedEnabled,
                QuotaLimit.of(e.stixExportMaxObjects),
                QuotaLimit.of(e.maxManualSubmissionsPerDay),
                QuotaLimit.of(e.maxImportRowsPerFile));
    }

    default void updateEntity(Plan plan, @MappingTarget PlanEntity e) {
        e.id = plan.id().value();
        e.code = plan.code().name();
        e.name = plan.name();
        e.tier = (short) plan.tier();
        e.requestsPerMinute = (int) plan.requestsPerMinute().orElse(0);
        e.requestsPerDay = nullableInt(plan.requestsPerDay());
        e.maxPageSize = plan.maxPageSize();
        e.maxBatchLookup = (int) plan.maxBatchLookup().orElse(0);
        e.minSyncIntervalSeconds = plan.minSyncIntervalSeconds();
        e.publicBloomEnabled = plan.publicBloomEnabled();
        e.tenantBloomCapacity = plan.tenantBloomCapacity().isUnlimited()
                ? null
                : plan.tenantBloomCapacity().orElse(0);
        e.websocketEnabled = plan.websocketEnabled();
        e.maxWebhooks = (int) plan.maxWebhooks().orElse(0);
        e.maxApiKeys = (int) plan.maxApiKeys().orElse(0);
        e.customFeedEnabled = plan.customFeedEnabled();
        e.stixExportMaxObjects = nullableInt(plan.stixExportMaxObjects());
        e.maxManualSubmissionsPerDay = (int) plan.maxManualSubmissionsPerDay().orElse(0);
        e.maxImportRowsPerFile = (int) plan.maxImportRowsPerFile().orElse(0);
    }

    private static Integer nullableInt(QuotaLimit limit) {
        return limit.isUnlimited() ? null : (int) limit.orElse(0);
    }
}
