package com.ctip.infrastructure.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * indicators(docs/spec/04-data-dictionary.md 表 4)。tags 為 text[],物化多來源聯集(I9)。
 * 類別 public 供 security 套件的 TlpSpecifications 引用型別;欄位仍 package-private。
 */
@Entity
@Table(name = "indicators")
public class IndicatorEntity {

    @Id
    UUID id;

    @Column(name = "owner_tenant_id", nullable = false)
    UUID ownerTenantId;

    @Column(nullable = false, length = 16)
    String type;

    @Column(name = "hash_type", length = 16)
    String hashType;

    @Column(nullable = false, length = 2048)
    String value;

    @Column(name = "normalized_value", nullable = false, length = 2048)
    String normalizedValue;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 64)
    String fingerprint;

    @Column(name = "first_seen", nullable = false)
    Instant firstSeen;

    @Column(name = "last_seen", nullable = false)
    Instant lastSeen;

    @Column(name = "valid_from", nullable = false)
    Instant validFrom;

    @Column(name = "valid_until")
    Instant validUntil;

    @Column(nullable = false)
    short confidence;

    @Column(nullable = false, length = 16)
    String severity;

    @Column(nullable = false)
    short score;

    @Column(nullable = false, length = 16)
    String tlp;

    @Column(nullable = false, length = 16)
    String status;

    @Column(nullable = false, columnDefinition = "text[]")
    String[] tags;

    @Column(name = "source_count", nullable = false)
    short sourceCount;

    @Column(name = "deleted_at")
    Instant deletedAt;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    @OneToMany(mappedBy = "indicator", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    Set<IndicatorSourceEntity> sources = new HashSet<>();

    @OneToMany(mappedBy = "indicator", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    Set<HashRecordEntity> hashRecords = new HashSet<>();

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
