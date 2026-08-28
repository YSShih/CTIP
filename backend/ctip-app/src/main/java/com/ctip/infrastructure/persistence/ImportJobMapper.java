package com.ctip.infrastructure.persistence;

import com.ctip.application.ingestion.ImportFormat;
import com.ctip.application.ingestion.ImportJob;
import com.ctip.application.ingestion.ImportJobId;
import com.ctip.application.ingestion.ImportJobStatus;
import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.user.UserId;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

/** ImportJob ↔ import_jobs 列(兩模型表)。 */
@Mapper(componentModel = "spring")
interface ImportJobMapper {

    default ImportJob toDomain(ImportJobEntity e) {
        return new ImportJob(
                new ImportJobId(e.id),
                new TenantId(e.tenantId),
                new UserId(e.submittedBy),
                ImportJobStatus.valueOf(e.status),
                ImportFormat.valueOf(e.format),
                e.totalRows,
                e.acceptedCount,
                e.mergedCount,
                e.rejectedCount,
                e.errorMessage,
                e.startedAt,
                e.finishedAt,
                e.createdAt);
    }

    default void updateEntity(ImportJob job, @MappingTarget ImportJobEntity e) {
        e.id = job.id().value();
        e.tenantId = job.tenantId().value();
        e.submittedBy = job.submittedBy().value();
        e.status = job.status().name();
        e.format = job.format().name();
        e.totalRows = job.totalRows();
        e.acceptedCount = job.acceptedCount();
        e.mergedCount = job.mergedCount();
        e.rejectedCount = job.rejectedCount();
        e.errorMessage = job.errorMessage();
        e.startedAt = job.startedAt();
        e.finishedAt = job.finishedAt();
        e.createdAt = job.createdAt();
    }
}
