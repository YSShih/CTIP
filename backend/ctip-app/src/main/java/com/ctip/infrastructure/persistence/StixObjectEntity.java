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

/** stix_objects(docs/spec/04-data-dictionary.md 表 8):STIX 2.1 衍生投影,可隨時由 domain 重建(兩模型)。 */
@Entity
@Table(name = "stix_objects")
class StixObjectEntity {

    @Id
    UUID id;

    @Column(name = "stix_id", nullable = false, length = 128)
    String stixId;

    @Column(name = "stix_type", nullable = false, length = 64)
    String stixType;

    @Column(name = "spec_version", nullable = false, length = 8)
    String specVersion;

    @Column(name = "owner_tenant_id", nullable = false)
    UUID ownerTenantId;

    @Column(name = "indicator_id")
    UUID indicatorId;

    @Column(name = "threat_id")
    UUID threatId;

    @Column(nullable = false, length = 16)
    String tlp;

    @Column(name = "stix_created", nullable = false)
    Instant stixCreated;

    @Column(name = "stix_modified", nullable = false)
    Instant stixModified;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    String content;

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
