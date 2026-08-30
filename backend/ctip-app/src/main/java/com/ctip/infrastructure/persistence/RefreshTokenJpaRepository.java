package com.ctip.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenEntity, UUID> {

    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

    List<RefreshTokenEntity> findByFamilyId(UUID familyId);

    List<RefreshTokenEntity> findByUserIdAndRevokedAtIsNull(UUID userId);

    int deleteByUserId(UUID userId);

    /**
     * 過期 token 清理(08 §8.7)。批次以 {@code id IN (SELECT … LIMIT n)} 表達,避免長交易鎖表;
     * {@code revoked_at IS NULL} 同時是批次的推進條件與「已撤銷不可清除」的保證。
     * 走 {@code ix_rt_gc}(表 15 的 {@code expires_at} 索引)。
     */
    @Modifying
    @Query(value = """
                    UPDATE refresh_tokens SET revoked_at = :now, revoked_reason = :reason
                    WHERE id IN (
                        SELECT id FROM refresh_tokens
                        WHERE expires_at <= :now AND revoked_at IS NULL
                        ORDER BY expires_at LIMIT :batchSize)
                    """, nativeQuery = true)
    int revokeExpired(@Param("now") Instant now, @Param("reason") String reason, @Param("batchSize") int batchSize);
}
