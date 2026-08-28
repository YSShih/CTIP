package com.ctip.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface SubscriptionJpaRepository extends JpaRepository<SubscriptionEntity, UUID> {

    Optional<SubscriptionEntity> findByTenantIdAndStatus(UUID tenantId, String status);

    @Query("select s.tenantId from SubscriptionEntity s where s.status = :status")
    List<UUID> findTenantIdsByStatus(String status);
}
