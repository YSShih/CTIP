package com.ctip.infrastructure.persistence;

import com.ctip.application.port.BloomVersionRepository;
import com.ctip.domain.bloom.BloomScope;
import com.ctip.domain.bloom.BloomVersion;
import com.ctip.domain.bloom.BloomVersionId;
import com.ctip.domain.tenant.TenantId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * BloomVersionRepository port 的 JPA 實作。
 * 版本一旦寫出即不再變更,{@link #save} 只在同一交易內建立兩列(版本 + artifact)。
 */
@Repository
@Transactional
class BloomVersionRepositoryAdapter implements BloomVersionRepository {

    private final BloomVersionJpaRepository versions;
    private final BloomArtifactJpaRepository artifacts;
    private final BloomVersionMapper mapper;

    BloomVersionRepositoryAdapter(
            BloomVersionJpaRepository versions, BloomArtifactJpaRepository artifacts, BloomVersionMapper mapper) {
        this.versions = versions;
        this.artifacts = artifacts;
        this.mapper = mapper;
    }

    @Override
    public BloomVersion save(BloomVersion version) {
        BloomVersionEntity entity = versions.findById(version.id().value()).orElseGet(BloomVersionEntity::new);
        mapper.updateEntity(version, entity);
        BloomVersionEntity savedVersion = versions.save(entity);

        BloomArtifactEntity artifact =
                artifacts.findByBloomVersionId(version.id().value()).orElseGet(BloomArtifactEntity::new);
        artifact.id = artifact.id == null ? UUID.randomUUID() : artifact.id;
        mapper.updateArtifact(version, artifact);
        return mapper.toDomain(savedVersion, artifacts.save(artifact));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BloomVersion> findLatest(BloomScope scope, TenantId tenantId) {
        return versions.findFirstByScopeAndTenantIdOrderByDatasetVersionDescBloomVersionDesc(
                        scope.name(), tenantId.value())
                .map(this::hydrate);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BloomVersion> findLatestFullSnapshot(BloomScope scope, TenantId tenantId) {
        return versions.findFirstByScopeAndTenantIdAndFullSnapshotTrueOrderByDatasetVersionDesc(
                        scope.name(), tenantId.value())
                .map(this::hydrate);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BloomVersion> findDeltaChain(BloomScope scope, TenantId tenantId, long datasetVersion) {
        return versions
                .findByScopeAndTenantIdAndDatasetVersionAndFullSnapshotFalseOrderByBloomVersionAsc(
                        scope.name(), tenantId.value(), datasetVersion)
                .stream()
                .map(this::hydrate)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BloomVersion> findNewestFirst(BloomScope scope, TenantId tenantId, int limit) {
        return versions
                .findByScopeAndTenantIdOrderByDatasetVersionDescBloomVersionDesc(
                        scope.name(), tenantId.value(), Limit.of(limit))
                .stream()
                .map(this::hydrate)
                .toList();
    }

    @Override
    public void recordDownload(BloomVersionId id) {
        artifacts.incrementDownloadCount(id.value());
    }

    @Override
    public void delete(BloomVersionId id) {
        // bloom_artifacts 的 FK 帶 ON DELETE CASCADE,artifact 列由資料庫一併移除
        versions.deleteById(id.value());
    }

    private BloomVersion hydrate(BloomVersionEntity entity) {
        BloomArtifactEntity artifact = artifacts
                .findByBloomVersionId(entity.id)
                .orElseThrow(() -> new IllegalStateException("bloom_version 缺少 artifact 列:" + entity.id));
        return mapper.toDomain(entity, artifact);
    }
}
