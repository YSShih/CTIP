package com.ctip.infrastructure.persistence;

import java.time.Instant;
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
}
