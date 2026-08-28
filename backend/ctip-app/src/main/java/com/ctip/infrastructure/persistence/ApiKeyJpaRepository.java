package com.ctip.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /** 只寫 last_used_at / updated_at,不碰 key_hash、scopes、revoked_at(見 ApiKeyRepository.markUsed)。 */
    @Modifying
    @Query("update ApiKeyEntity k set k.lastUsedAt = :usedAt, k.updatedAt = :usedAt where k.id = :id")
    void markUsed(@Param("id") UUID id, @Param("usedAt") Instant usedAt);
}
