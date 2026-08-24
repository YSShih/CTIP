package com.ctip.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * indicator_sources(docs/spec/04-data-dictionary.md 表 5):每個 (indicator, source) 一列,同來源 UPSERT。
 * 類別 public 供 TlpSpecifications 的再散布 EXISTS 子查詢引用;欄位仍 package-private。
 */
@Entity
@Table(name = "indicator_sources")
public class IndicatorSourceEntity {

    @Id
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "indicator_id", nullable = false)
    IndicatorEntity indicator;

    @Column(name = "source_id", nullable = false)
    UUID sourceId;

    @Column(name = "source_value", nullable = false, length = 2048)
    String sourceValue;

    @Column(name = "source_confidence")
    Short sourceConfidence;

    @Column(name = "source_severity", length = 16)
    String sourceSeverity;

    @Column(name = "source_tlp", nullable = false, length = 16)
    String sourceTlp;

    @Column(name = "source_first_seen", nullable = false)
    Instant sourceFirstSeen;

    @Column(name = "source_last_seen", nullable = false)
    Instant sourceLastSeen;

    @Column(name = "source_valid_until")
    Instant sourceValidUntil;

    @Column(name = "redistribution_policy", nullable = false, length = 32)
    String redistributionPolicy;

    @Column(name = "report_count", nullable = false)
    int reportCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", columnDefinition = "jsonb")
    String rawPayload;

    @Column(nullable = false, length = 16)
    String status;

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
