package com.ctip.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** users(docs/spec/04-data-dictionary.md 表 10)。password_hash 只存雜湊,原文絕不進入本物件。 */
@Entity
@Table(name = "users")
class UserEntity {

    @Id
    UUID id;

    @Column(nullable = false, length = 320)
    String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    String passwordHash;

    @Column(name = "display_name", length = 255)
    String displayName;

    @Column(nullable = false, length = 32)
    String status;

    @Column(name = "primary_tenant_id", nullable = false)
    UUID primaryTenantId;

    @Column(name = "last_login_at")
    Instant lastLoginAt;

    @Column(name = "failed_login_count", nullable = false)
    short failedLoginCount;

    @Column(name = "locked_until")
    Instant lockedUntil;

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
