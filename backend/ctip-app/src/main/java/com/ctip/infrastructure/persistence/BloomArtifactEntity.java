package com.ctip.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** bloom_artifacts(docs/spec/04-data-dictionary.md 表 23)。每個 bloom_version 恰一列(ux_ba_version)。 */
@Entity
@Table(name = "bloom_artifacts")
class BloomArtifactEntity {

    @Id
    UUID id;

    @Column(name = "bloom_version_id", nullable = false)
    UUID bloomVersionId;

    @Column(name = "storage_kind", nullable = false, length = 16)
    String storageKind;

    @Column(name = "storage_path", nullable = false, length = 1024)
    String storagePath;

    @Column(nullable = false, length = 8)
    String compression;

    @Column(name = "size_bytes", nullable = false)
    long sizeBytes;

    @Column(name = "uncompressed_size_bytes", nullable = false)
    long uncompressedSizeBytes;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 64)
    String checksum;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "resulting_checksum", length = 64)
    String resultingChecksum;

    @Column(name = "download_count", nullable = false)
    long downloadCount;

    @Column(name = "expires_at")
    Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }
}
