package com.ctip.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** source_sync(docs/spec/04-data-dictionary.md 表 3):每次 ingestion 一列,append-only(兩模型,無 domain model)。 */
@Entity
@Table(name = "source_sync")
class SourceSyncEntity {

    @Id
    UUID id;

    @Column(name = "source_id", nullable = false)
    UUID sourceId;

    @Column(name = "started_at", nullable = false)
    Instant startedAt;

    @Column(name = "finished_at")
    Instant finishedAt;

    @Column(name = "duration_ms")
    Integer durationMs;

    @Column(nullable = false, length = 32)
    String result;

    @Column(name = "records_fetched", nullable = false)
    int recordsFetched;

    @Column(name = "records_accepted", nullable = false)
    int recordsAccepted;

    @Column(name = "records_rejected", nullable = false)
    int recordsRejected;

    @Column(name = "records_merged", nullable = false)
    int recordsMerged;

    @Column(name = "error_message", columnDefinition = "text")
    String errorMessage;

    @Column(name = "trace_id", length = 64)
    String traceId;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }
}
