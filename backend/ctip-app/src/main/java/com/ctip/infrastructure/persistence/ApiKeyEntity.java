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

/** api_keys(docs/spec/04-data-dictionary.md 表 16)。只存前綴與 SHA-256 雜湊,原文不落庫。 */
@Entity
@Table(name = "api_keys")
class ApiKeyEntity {

    @Id
    UUID id;

    @Column(name = "tenant_id", nullable = false)
    UUID tenantId;

    @Column(name = "user_id", nullable = false)
    UUID userId;

    @Column(nullable = false, length = 128)
    String name;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "key_prefix", nullable = false, length = 8)
    String keyPrefix;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "key_hash", nullable = false, length = 64)
    String keyHash;

    @Column(nullable = false, columnDefinition = "text[]")
    String[] scopes;

    @Column(name = "expires_at")
    Instant expiresAt;

    @Column(name = "last_used_at")
    Instant lastUsedAt;

    @Column(name = "revoked_at")
    Instant revokedAt;

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
