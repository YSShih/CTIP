package com.ctip.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ApiKeyJpaRepository extends JpaRepository<ApiKeyEntity, UUID> {

    Optional<ApiKeyEntity> findByKeyPrefix(String keyPrefix);

    List<ApiKeyEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    @Query("""
            select count(k) from ApiKeyEntity k
            where k.tenantId = :tenantId
              and k.revokedAt is null
              and (k.expiresAt is null or k.expiresAt > :now)
            """)
    long countActive(@Param("tenantId") UUID tenantId, @Param("now") Instant now);
}
