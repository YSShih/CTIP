package com.ctip.infrastructure.persistence;

import com.ctip.application.port.WebhookRepository;
import com.ctip.domain.notification.Webhook;
import com.ctip.domain.notification.WebhookId;
import com.ctip.domain.notification.WebhookStatus;
import com.ctip.domain.tenant.TenantId;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** WebhookRepository port 的 JPA 實作(docs/spec/04-data-dictionary.md 表 24)。 */
@Repository
@Transactional
class WebhookRepositoryAdapter implements WebhookRepository {

    private final WebhookJpaRepository jpa;
    private final WebhookMapper mapper;

    WebhookRepositoryAdapter(WebhookJpaRepository jpa, WebhookMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public Webhook save(Webhook webhook) {
        WebhookEntity entity = jpa.findById(webhook.id().value()).orElseGet(WebhookEntity::new);
        mapper.updateEntity(webhook, entity);
        return mapper.toDomain(jpa.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Webhook> findById(WebhookId id) {
        return jpa.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Webhook> findByTenant(TenantId tenantId) {
        return jpa.findByTenantIdOrderByCreatedAtDesc(tenantId.value()).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Webhook> findAllActive() {
        return jpa.findByStatus(WebhookStatus.ACTIVE.name()).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countNotDisabled(TenantId tenantId) {
        return jpa.countByTenantIdAndStatusNot(tenantId.value(), WebhookStatus.DISABLED.name());
    }

    @Override
    public boolean delete(WebhookId id, TenantId tenantId) {
        return jpa.findByIdAndTenantId(id.value(), tenantId.value())
                .map(entity -> {
                    jpa.delete(entity);
                    return true;
                })
                .orElse(false);
    }
}
