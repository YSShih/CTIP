package com.ctip.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ImportJobJpaRepository extends JpaRepository<ImportJobEntity, UUID> {

    Optional<ImportJobEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
