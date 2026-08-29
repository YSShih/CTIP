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

    /**
     * 多來源合併把 TLP 收緊時發佈(§7.7「合併取最嚴格」)。
     *
     * <p>消費端是 Threat 的 H6 一致性規則:{@code Threat.tlp} 不得比任一關聯 Indicator 更寬鬆,
     * 而 Indicator 的 TLP 會在合併時事後收緊——沒有這個事件,已建立的關聯就會停在較寬鬆的
     * Threat TLP 上,H6 只在建立關聯的那一刻成立(ADR 0020 定調,Phase 18 交付)。
     */
    record IndicatorTlpTightened(IndicatorId indicatorId, TenantId tenantId, Tlp previousTlp, Tlp currentTlp)
            implements DomainEvent {}
}
