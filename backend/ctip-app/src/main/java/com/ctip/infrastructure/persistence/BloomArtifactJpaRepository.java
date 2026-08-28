package com.ctip.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface BloomArtifactJpaRepository extends JpaRepository<BloomArtifactEntity, UUID> {

    Optional<BloomArtifactEntity> findByBloomVersionId(UUID bloomVersionId);
}
