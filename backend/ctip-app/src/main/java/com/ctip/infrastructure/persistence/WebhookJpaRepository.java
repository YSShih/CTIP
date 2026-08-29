package com.ctip.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface WebhookJpaRepository extends JpaRepository<WebhookEntity, UUID> {

    List<WebhookEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<WebhookEntity> findByStatus(String status);

    long countByTenantIdAndStatusNot(UUID tenantId, String status);

    Optional<WebhookEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
