package com.ctip.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** tenant_users(表 14)。PK 為 (tenant_id, user_id):一使用者在一租戶內恰一個角色。 */
@Entity
@Table(name = "tenant_users")
@IdClass(TenantUserKey.class)
class TenantUserEntity {

    @Id
    @Column(name = "tenant_id", nullable = false)
    UUID tenantId;

    @Id
    @Column(name = "user_id", nullable = false)
    UUID userId;

    @Column(name = "role_id", nullable = false)
    UUID roleId;

    @Column(name = "joined_at", nullable = false)
    Instant joinedAt;

    @PrePersist
    void onCreate() {
        joinedAt = joinedAt == null ? Instant.now() : joinedAt;
    }
}
