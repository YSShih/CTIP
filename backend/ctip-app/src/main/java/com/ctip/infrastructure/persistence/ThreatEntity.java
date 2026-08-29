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

/**
 * threats(docs/spec/04-data-dictionary.md 表 19)。aliases / tags 為 text[]。
 * 類別 public 供 security 套件的 ThreatSpecifications 引用型別;欄位仍 package-private。
 */
@Entity
@Table(name = "threats")
public class ThreatEntity {

    @Id
    UUID id;

    @Column(name = "owner_tenant_id", nullable = false)
    UUID ownerTenantId;

    @Column(nullable = false, length = 32)
    String type;

    @Column(nullable = false, length = 255)
    String name;

    @Column(nullable = false, columnDefinition = "text[]")
    String[] aliases;

    @Column(columnDefinition = "text")
    String description;

    @Column(nullable = false, length = 16)
    String severity;

    @Column(nullable = false)
    short confidence;

    @Column(nullable = false, length = 16)
    String tlp;

    @Column(nullable = false, length = 16)
    String status;

    @Column(name = "first_seen", nullable = false)
    Instant firstSeen;

    @Column(name = "last_seen", nullable = false)
    Instant lastSeen;

    @Column(nullable = false, columnDefinition = "text[]")
    String[] tags;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    @OneToMany(mappedBy = "threat", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    Set<ThreatIndicatorEntity> indicators = new HashSet<>();

    @OneToMany(mappedBy = "threat", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    Set<ThreatExternalReferenceEntity> externalReferences = new HashSet<>();

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
