package com.ctip.domain.event;

import com.ctip.domain.indicator.IndicatorId;
import com.ctip.domain.source.SourceId;
import com.ctip.domain.tenant.TenantId;
import com.ctip.sdk.IocType;
import com.ctip.sdk.Tlp;
import java.time.Instant;

/** Indicator 聚合發佈的 M1 事件(docs/spec/02-ddd-model.md §2.4)。 */
public interface IndicatorEvents {

    record IndicatorCreated(IndicatorId indicatorId, TenantId tenantId, IocType type, String normalizedValue, Tlp tlp)
            implements DomainEvent {}

    record IndicatorMerged(IndicatorId indicatorId, TenantId tenantId, SourceId mergedSourceId)
            implements DomainEvent {}

    record IndicatorExpired(IndicatorId indicatorId, TenantId tenantId, Instant expiredAt) implements DomainEvent {}

    record IndicatorRevoked(IndicatorId indicatorId, TenantId tenantId, SourceId revokedBy) implements DomainEvent {}

    record IndicatorFalsePositiveReported(IndicatorId indicatorId, TenantId tenantId, SourceId reportedBy)
            implements DomainEvent {}
}
