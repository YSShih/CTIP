package com.ctip.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** import_jobs(docs/spec/04-data-dictionary.md 表 18b):非同步匯入的狀態承載。 */
@Entity
@Table(name = "import_jobs")
class ImportJobEntity {

    @Id
    UUID id;

    @Column(name = "tenant_id", nullable = false)
    UUID tenantId;

    @Column(name = "submitted_by", nullable = false)
    UUID submittedBy;

    @Column(nullable = false, length = 16)
    String status;

    @Column(nullable = false, length = 16)
    String format;

    @Column(name = "total_rows")
    Integer totalRows;

    @Column(name = "accepted_count", nullable = false)
    int acceptedCount;

    @Column(name = "merged_count", nullable = false)
    int mergedCount;

    @Column(name = "rejected_count", nullable = false)
    int rejectedCount;

    @Column(name = "error_message", length = 1024)
    String errorMessage;

    @Column(name = "started_at")
    Instant startedAt;

    @Column(name = "finished_at")
    Instant finishedAt;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = createdAt == null ? now : createdAt;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
