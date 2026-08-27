package com.ctip.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface TenantUserJpaRepository extends JpaRepository<TenantUserEntity, TenantUserKey> {

    Optional<TenantUserEntity> findByTenantIdAndUserId(UUID tenantId, UUID userId);
}
