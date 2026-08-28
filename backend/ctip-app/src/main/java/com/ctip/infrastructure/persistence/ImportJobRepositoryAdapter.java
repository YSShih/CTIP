package com.ctip.infrastructure.persistence;

import com.ctip.application.ingestion.ImportJob;
import com.ctip.application.ingestion.ImportJobId;
import com.ctip.application.port.ImportJobRepository;
import com.ctip.domain.tenant.TenantId;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** ImportJobRepository port 的 JPA 實作;查詢一律帶 tenantId(跨租戶視為不存在)。 */
@Repository
@Transactional
class ImportJobRepositoryAdapter implements ImportJobRepository {

    private final ImportJobJpaRepository jpa;
    private final ImportJobMapper mapper;

    ImportJobRepositoryAdapter(ImportJobJpaRepository jpa, ImportJobMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ImportJob> find(ImportJobId id, TenantId tenantId) {
        return jpa.findByIdAndTenantId(id.value(), tenantId.value()).map(mapper::toDomain);
    }

    @Override
    public ImportJob save(ImportJob job) {
        ImportJobEntity entity = jpa.findById(job.id().value()).orElseGet(ImportJobEntity::new);
        mapper.updateEntity(job, entity);
        return mapper.toDomain(jpa.save(entity));
    }
}
