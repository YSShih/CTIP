package com.ctip.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

/**
 * package-private:只有同 package 的 adapter 看得到(docs/spec/01-architecture.md §1.6)。
 * 分頁查詢用 JpaSpecificationExecutor 的 fluent findBy(spec, query),無 COUNT query。
 */
interface ThreatJpaRepository extends JpaRepository<ThreatEntity, UUID>, JpaSpecificationExecutor<ThreatEntity> {

    @EntityGraph(attributePaths = {"indicators", "externalReferences"})
    Optional<ThreatEntity> findWithDetailsById(UUID id);

    @EntityGraph(attributePaths = {"indicators", "externalReferences"})
    Optional<ThreatEntity> findByOwnerTenantIdAndTypeAndName(UUID ownerTenantId, String type, String name);

    /** H6 的重新收緊:所有關聯到該 indicator 的 Threat(吃 ix_ti_indicator)。 */
    @EntityGraph(attributePaths = {"indicators", "externalReferences"})
    @Query("select distinct t from ThreatEntity t join t.indicators link where link.indicatorId = :indicatorId")
    List<ThreatEntity> findByLinkedIndicator(UUID indicatorId);
}
