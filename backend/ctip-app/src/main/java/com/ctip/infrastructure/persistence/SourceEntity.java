package com.ctip.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** sources(docs/spec/04-data-dictionary.md 表 2)。config 只存環境變數名稱參照,不存憑證(S6)。 */
@Entity
@Table(name = "sources")
class SourceEntity {

    @Id
    UUID id;

    @Column(name = "source_type", nullable = false, length = 64)
    String sourceType;

    @Column(name = "display_name", nullable = false)
    String displayName;

    @Column(columnDefinition = "text")
    String description;

    @Column(name = "homepage_url", length = 2048)
    String homepageUrl;

    @Column(name = "default_tlp", nullable = false, length = 16)
    String defaultTlp;

    @Column(name = "redistribution_policy", nullable = false, length = 32)
    String redistributionPolicy;

    @Column(nullable = false)
    short reputation;

    @Column(nullable = false)
    boolean enabled;

    @Column(nullable = false)
    boolean syncable;

    @Column(name = "recommended_interval_seconds")
    Integer recommendedIntervalSeconds;

    @Column(name = "requires_credentials", nullable = false)
    boolean requiresCredentials;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    String config;

    @Column(nullable = false, length = 32)
    String status;

    @Column(name = "consecutive_failures", nullable = false)
    int consecutiveFailures;

    @Column(name = "last_sync_at")
    Instant lastSyncAt;

    @Column(name = "last_success_at")
    Instant lastSuccessAt;

    @Column(name = "last_failure_at")
    Instant lastFailureAt;

    @Column(name = "last_error_message", columnDefinition = "text")
    String lastErrorMessage;

    @Column(name = "avg_latency_ms")
    Integer avgLatencyMs;

    @Column(name = "total_records_ingested", nullable = false)
    long totalRecordsIngested;

    @Column(name = "next_cursor", length = 1024)
    String nextCursor;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = createdAt == null ? now : createdAt;
        updatedAt = now;
        config = config == null ? "{}" : config;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
