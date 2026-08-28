package com.ctip.infrastructure.persistence;

import com.ctip.domain.bloom.BloomArtifact;
import com.ctip.domain.bloom.BloomCompression;
import com.ctip.domain.bloom.BloomParameters;
import com.ctip.domain.bloom.BloomScope;
import com.ctip.domain.bloom.BloomStorageKind;
import com.ctip.domain.bloom.BloomVersion;
import com.ctip.domain.bloom.BloomVersionId;
import com.ctip.domain.bloom.BloomVersionSnapshot;
import com.ctip.domain.bloom.Checksum;
import com.ctip.domain.tenant.TenantId;
import com.ctip.sdk.FingerprintAlgorithm;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

/** BloomVersion 聚合 ↔ 兩張表的 entity。artifact 由 1:1 關聯(ux_ba_version)載入後傳入。 */
@Mapper(componentModel = "spring")
interface BloomVersionMapper {

    default BloomVersion toDomain(BloomVersionEntity e, BloomArtifactEntity a) {
        return BloomVersion.reconstitute(new BloomVersionSnapshot(
                new BloomVersionId(e.id),
                BloomScope.valueOf(e.scope),
                new TenantId(e.tenantId),
                e.datasetVersion,
                e.bloomVersion,
                new BloomParameters(
                        FingerprintAlgorithm.valueOf(e.fingerprintAlgorithm),
                        e.hashFunctionCount,
                        e.bitSize,
                        e.capacity,
                        e.falsePositiveRate),
                e.memberCount,
                e.fullSnapshot,
                e.baseBloomVersion,
                e.generatedAt,
                toArtifact(a)));
    }

    default BloomArtifact toArtifact(BloomArtifactEntity a) {
        return new BloomArtifact(
                BloomStorageKind.valueOf(a.storageKind),
                a.storagePath,
                BloomCompression.valueOf(a.compression),
                a.sizeBytes,
                a.uncompressedSizeBytes,
                new Checksum(a.checksum.trim()),
                a.resultingChecksum == null ? null : new Checksum(a.resultingChecksum.trim()),
                a.downloadCount,
                a.expiresAt);
    }

    default void updateEntity(BloomVersion version, @MappingTarget BloomVersionEntity e) {
        BloomVersionSnapshot s = version.snapshot();
        e.id = s.id().value();
        e.scope = s.scope().name();
        e.tenantId = s.tenantId().value();
        e.datasetVersion = s.datasetVersion();
        e.bloomVersion = s.bloomVersion();
        e.fingerprintAlgorithm = s.parameters().algorithm().name();
        e.hashFunctionCount = (short) s.parameters().hashFunctionCount();
        e.bitSize = s.parameters().bitSize();
        e.capacity = s.parameters().capacity();
        e.falsePositiveRate = s.parameters().falsePositiveRate();
        e.memberCount = s.memberCount();
        e.fullSnapshot = s.fullSnapshot();
        e.baseBloomVersion = s.baseBloomVersion();
        e.generatedAt = s.generatedAt();
    }

    default void updateArtifact(BloomVersion version, @MappingTarget BloomArtifactEntity a) {
        BloomArtifact artifact = version.artifact();
        a.bloomVersionId = version.id().value();
        a.storageKind = artifact.storageKind().name();
        a.storagePath = artifact.storagePath();
        a.compression = artifact.compression().name();
        a.sizeBytes = artifact.sizeBytes();
        a.uncompressedSizeBytes = artifact.uncompressedSizeBytes();
        a.checksum = artifact.checksum().hex();
        a.resultingChecksum = artifact.resultingChecksum() == null
                ? null
                : artifact.resultingChecksum().hex();
        a.downloadCount = artifact.downloadCount();
        a.expiresAt = artifact.expiresAt();
    }
}
