package com.ctip.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface BloomArtifactJpaRepository extends JpaRepository<BloomArtifactEntity, UUID> {

    Optional<BloomArtifactEntity> findByBloomVersionId(UUID bloomVersionId);

    /** 定向 +1:整列覆寫會與排程的生成互相沖掉(見 port 的說明)。 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update BloomArtifactEntity a set a.downloadCount = a.downloadCount + 1 "
            + "where a.bloomVersionId = :bloomVersionId")
    int incrementDownloadCount(@Param("bloomVersionId") UUID bloomVersionId);
}
