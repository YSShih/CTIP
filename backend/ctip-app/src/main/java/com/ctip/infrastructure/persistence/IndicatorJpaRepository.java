package com.ctip.infrastructure.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * package-private:只有同 package 的 adapter 看得到(docs/spec/01-architecture.md §1.6)。
 * 分頁查詢用 JpaSpecificationExecutor 的 fluent findBy(spec, query),無 COUNT query。
 */
interface IndicatorJpaRepository
        extends JpaRepository<IndicatorEntity, UUID>, JpaSpecificationExecutor<IndicatorEntity> {

    @EntityGraph(attributePaths = {"sources", "hashRecords"})
    Optional<IndicatorEntity> findWithSourcesById(UUID id);

    @EntityGraph(attributePaths = {"sources", "hashRecords"})
    Optional<IndicatorEntity> findByTypeAndNormalizedValueAndOwnerTenantId(
            String type, String normalizedValue, UUID ownerTenantId);

    @EntityGraph(attributePaths = {"sources", "hashRecords"})
    List<IndicatorEntity> findByStatusAndValidUntilBefore(String status, Instant validUntil, Limit limit);

    // 搜尋文件投影(§13.7):一律排除軟刪除,並帶出來源的再散布政策(可見度與側信道防護所需)
    @EntityGraph(attributePaths = "sources")
    List<IndicatorEntity> findByDeletedAtIsNullAndIdIn(Collection<UUID> ids);

    @EntityGraph(attributePaths = "sources")
    List<IndicatorEntity> findByDeletedAtIsNullOrderByIdAsc(Limit limit);

    @EntityGraph(attributePaths = "sources")
    List<IndicatorEntity> findByDeletedAtIsNullAndIdGreaterThanOrderByIdAsc(UUID afterId, Limit limit);

    long countByDeletedAtIsNull();
}
