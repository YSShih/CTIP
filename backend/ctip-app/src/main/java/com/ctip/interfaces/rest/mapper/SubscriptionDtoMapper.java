package com.ctip.interfaces.rest.mapper;

import com.ctip.application.port.RateLimitResult;
import com.ctip.domain.plan.Plan;
import com.ctip.domain.plan.QuotaLimit;
import com.ctip.domain.plan.Subscription;
import com.ctip.interfaces.rest.dto.subscription.PlanQuotasDto;
import com.ctip.interfaces.rest.dto.subscription.SubscriptionDto;
import com.ctip.interfaces.rest.dto.subscription.SubscriptionUsageDto;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** 方案／訂閱 → DTO(手寫;§9.5 禁止把 entity 或聚合直接暴露於 API)。 */
@Component
public class SubscriptionDtoMapper {

    public SubscriptionDto toDto(Plan plan, Optional<Subscription> subscription) {
        return new SubscriptionDto(
                plan.code().name(),
                plan.name(),
                plan.tier(),
                subscription.map(s -> s.status().name()).orElse(null),
                subscription.map(s -> s.provider().name()).orElse(null),
                subscription.map(s -> s.period().start()).orElse(null),
                subscription.map(s -> s.period().end()).orElse(null),
                subscription.map(Subscription::cancelledAt).orElse(null),
                toQuotas(plan));
    }

    public PlanQuotasDto toQuotas(Plan plan) {
        return new PlanQuotasDto(
                value(plan.requestsPerMinute()),
                value(plan.requestsPerDay()),
                plan.maxPageSize(),
                value(plan.maxBatchLookup()),
                plan.minSyncIntervalSeconds(),
                plan.publicBloomEnabled(),
                value(plan.tenantBloomCapacity()),
                plan.websocketEnabled(),
                value(plan.maxWebhooks()),
                value(plan.maxApiKeys()),
                plan.customFeedEnabled(),
                value(plan.stixExportMaxObjects()),
                value(plan.maxManualSubmissionsPerDay()),
                value(plan.maxImportRowsPerFile()));
    }

    public SubscriptionUsageDto toUsage(Plan plan, RateLimitResult submissions, long activeApiKeys) {
        return new SubscriptionUsageDto(
                plan.code().name(),
                new SubscriptionUsageDto.UsageItem(
                        submissions.used(), value(submissions.limit()), submissions.resetAt()),
                new SubscriptionUsageDto.UsageItem(activeApiKeys, value(plan.maxApiKeys()), null));
    }

    /** 無限制 → null(JSON 的 null 就是「沒有上限」;印任何數字都會被當成真實上限)。 */
    private static Long value(QuotaLimit limit) {
        return limit.isUnlimited() ? null : limit.orElse(0);
    }
}
