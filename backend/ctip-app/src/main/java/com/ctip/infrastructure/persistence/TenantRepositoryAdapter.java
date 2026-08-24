package com.ctip.infrastructure.persistence;

import com.ctip.application.port.TenantRepository;
import com.ctip.domain.tenant.Tenant;
import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.tenant.TenantSlug;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** TenantRepository port 的 JPA 實作(docs/spec/01-architecture.md §1.6)。 */
@Repository
@Transactional
class TenantRepositoryAdapter implements TenantRepository {

    private final TenantJpaRepository jpa;
    private final TenantMapper mapper;

    TenantRepositoryAdapter(TenantJpaRepository jpa, TenantMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Tenant> findById(TenantId id) {
        return jpa.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Tenant> findBySlug(TenantSlug slug) {
        return jpa.findBySlug(slug.value()).map(mapper::toDomain);
    }

    @Override
    public Tenant save(Tenant tenant) {
        TenantEntity entity = jpa.findById(tenant.id().value()).orElseGet(TenantEntity::new);
        mapper.updateEntity(tenant, entity);
        return mapper.toDomain(jpa.save(entity));
    }
}
