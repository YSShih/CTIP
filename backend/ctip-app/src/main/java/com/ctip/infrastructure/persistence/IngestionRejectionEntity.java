package com.ctip.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** ingestion_rejections(docs/spec/04-data-dictionary.md 表 7):append-only,保留 30 天(兩模型)。 */
@Entity
@Table(name = "ingestion_rejections")
class IngestionRejectionEntity {

    @Id
    UUID id;

    @Column(name = "source_id", nullable = false)
    UUID sourceId;

    @Column(name = "source_sync_id")
    UUID sourceSyncId;

    @Column(name = "import_job_id")
    UUID importJobId;

    @Column(name = "raw_value", nullable = false, length = 4096)
    String rawValue;

    @Column(name = "declared_type", length = 16)
    String declaredType;

    @Column(nullable = false, length = 64)
    String reason;

    @Column(columnDefinition = "text")
    String detail;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }
}
