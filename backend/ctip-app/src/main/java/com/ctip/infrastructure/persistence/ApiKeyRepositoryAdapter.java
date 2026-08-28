package com.ctip.infrastructure.persistence;

import com.ctip.application.port.ApiKeyRepository;
import com.ctip.application.port.ClockPort;
import com.ctip.domain.identity.ApiKey;
import com.ctip.domain.identity.ApiKeyId;
import com.ctip.domain.identity.KeyPrefix;
import com.ctip.domain.tenant.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** ApiKeyRepository port 的 JPA 實作;前綴定位走 ux_api_keys_prefix 唯一索引。 */
@Repository
@Transactional
class ApiKeyRepositoryAdapter implements ApiKeyRepository {

    private final ApiKeyJpaRepository jpa;
    private final ApiKeyMapper mapper;
    private final ClockPort clock;

    ApiKeyRepositoryAdapter(ApiKeyJpaRepository jpa, ApiKeyMapper mapper, ClockPort clock) {
        this.jpa = jpa;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ApiKey> findByPrefix(KeyPrefix prefix) {
        return jpa.findByKeyPrefix(prefix.value()).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ApiKey> findById(ApiKeyId id) {
        return jpa.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApiKey> findByTenant(TenantId tenantId) {
        return jpa.findByTenantIdOrderByCreatedAtDesc(tenantId.value()).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countActive(TenantId tenantId) {
        return jpa.countActive(tenantId.value(), clock.now());
    }

    @Override
    public void markUsed(ApiKeyId id, Instant usedAt) {
        jpa.markUsed(id.value(), usedAt);
    }

    @Override
    public ApiKey save(ApiKey apiKey) {
        ApiKeyEntity entity = jpa.findById(apiKey.id().value()).orElseGet(ApiKeyEntity::new);
        mapper.updateEntity(apiKey, entity);
        return mapper.toDomain(jpa.save(entity));
    }
}
