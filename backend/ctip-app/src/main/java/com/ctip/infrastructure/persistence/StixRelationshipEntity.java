package com.ctip.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** stix_relationships(docs/spec/04-data-dictionary.md 表 9):STIX 關聯的衍生投影(兩模型)。 */
@Entity
@Table(name = "stix_relationships")
class StixRelationshipEntity {

    @Id
    UUID id;

    @Column(name = "stix_id", nullable = false, length = 128)
    String stixId;

    @Column(name = "relationship_type", nullable = false, length = 64)
    String relationshipType;

    @Column(name = "source_ref", nullable = false, length = 128)
    String sourceRef;

    @Column(name = "target_ref", nullable = false, length = 128)
    String targetRef;

    @Column(name = "owner_tenant_id", nullable = false)
    UUID ownerTenantId;

    @Column(nullable = false, length = 16)
    String tlp;

    @Column(name = "stix_created", nullable = false)
    Instant stixCreated;

    @Column(name = "stix_modified", nullable = false)
    Instant stixModified;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }
}
