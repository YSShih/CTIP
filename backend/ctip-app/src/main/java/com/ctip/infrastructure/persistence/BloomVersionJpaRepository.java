package com.ctip.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

interface BloomVersionJpaRepository extends JpaRepository<BloomVersionEntity, UUID> {

    Optional<BloomVersionEntity> findFirstByScopeAndTenantIdOrderByDatasetVersionDescBloomVersionDesc(
            String scope, UUID tenantId);

    Optional<BloomVersionEntity> findFirstByScopeAndTenantIdAndFullSnapshotTrueOrderByDatasetVersionDesc(
            String scope, UUID tenantId);

    List<BloomVersionEntity> findByScopeAndTenantIdAndDatasetVersionAndFullSnapshotFalseOrderByBloomVersionAsc(
            String scope, UUID tenantId, long datasetVersion);

    List<BloomVersionEntity> findByScopeAndTenantIdOrderByDatasetVersionDescBloomVersionDesc(
            String scope, UUID tenantId, Limit limit);
}
