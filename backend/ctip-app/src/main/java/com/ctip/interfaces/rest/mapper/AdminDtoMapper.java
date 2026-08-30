package com.ctip.interfaces.rest.mapper;

import com.ctip.application.admin.DataSubjectErasure;
import com.ctip.application.admin.DataSubjectReport;
import com.ctip.application.admin.TenantOverview;
import com.ctip.application.source.SourceSyncOutcome;
import com.ctip.domain.plan.Subscription;
import com.ctip.domain.source.Source;
import com.ctip.interfaces.rest.dto.admin.DataSubjectErasureDto;
import com.ctip.interfaces.rest.dto.admin.DataSubjectReportDto;
import com.ctip.interfaces.rest.dto.admin.SourceAdminDto;
import com.ctip.interfaces.rest.dto.admin.SourceSyncResultDto;
import com.ctip.interfaces.rest.dto.admin.SubscriptionAssignmentDto;
import com.ctip.interfaces.rest.dto.admin.TenantOverviewDto;
import org.mapstruct.Mapper;

/** 管理端點的 DTO 映射(docs/spec/09-api.md §9.1「管理」)。 */
@Mapper(componentModel = "spring")
public interface AdminDtoMapper {

    default TenantOverviewDto toDto(TenantOverview overview) {
        return new TenantOverviewDto(
                overview.id().value(),
                overview.slug(),
                overview.name(),
                overview.type().name(),
                overview.status().name(),
                overview.planCode().name());
    }

    default SubscriptionAssignmentDto toDto(Subscription subscription) {
        return new SubscriptionAssignmentDto(
                subscription.snapshot().id().value(),
                subscription.snapshot().tenantId().value(),
                subscription.planCode().name(),
                subscription.snapshot().status().name(),
                subscription.snapshot().cancelledAt());
    }

    default SourceAdminDto toDto(Source source) {
        return new SourceAdminDto(
                source.id().value(), source.enabled(), source.health().status().name(), source.lastErrorMessage());
    }

    default SourceSyncResultDto toDto(SourceSyncOutcome outcome) {
        return new SourceSyncResultDto(
                outcome.sourceId().value(),
                outcome.success(),
                outcome.recordsFetched(),
                outcome.recordsAccepted(),
                outcome.recordsRejected(),
                outcome.recordsMerged(),
                outcome.errorMessage());
    }

    default DataSubjectReportDto toDto(DataSubjectReport report) {
        return new DataSubjectReportDto(
                report.userId(),
                report.email(),
                report.displayName(),
                report.status(),
                report.lastLoginAt(),
                report.activeRefreshTokens(),
                report.auditTrail().count(),
                report.auditTrail().earliest(),
                report.auditTrail().latest());
    }

    default DataSubjectErasureDto toDto(DataSubjectErasure erasure) {
        return new DataSubjectErasureDto(
                erasure.userId(),
                erasure.deletedRefreshTokens(),
                erasure.retainedAuditEntries().count());
    }
}
