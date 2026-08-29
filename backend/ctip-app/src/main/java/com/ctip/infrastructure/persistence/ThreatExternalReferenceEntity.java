package com.ctip.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * threat_external_references(docs/spec/04-data-dictionary.md 表 21):Threat 內的值物件集合。
 * H4 由 {@code ux_ter_identity_coalesced} 唯一索引與聚合共同強制。
 */
@Entity
@Table(name = "threat_external_references")
class ThreatExternalReferenceEntity {

    @Id
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "threat_id", nullable = false)
    ThreatEntity threat;

    @Column(name = "source_name", nullable = false, length = 64)
    String sourceName;

    @Column(name = "external_id", length = 128)
    String externalId;

    @Column(length = 2048)
    String url;

    @Column(columnDefinition = "text")
    String description;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }
}
