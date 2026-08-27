package com.ctip.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * refresh_tokens(docs/spec/04-data-dictionary.md 表 15)。token_hash 為 SHA-256 的 hex 64 碼;
 * 本表無 updated_at(表定義如此),終態欄位以 UPDATE 回寫。
 */
@Entity
@Table(name = "refresh_tokens")
class RefreshTokenEntity {

    @Id
    UUID id;

    @Column(name = "user_id", nullable = false)
    UUID userId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", nullable = false, length = 64)
    String tokenHash;

    @Column(name = "family_id", nullable = false)
    UUID familyId;

    @Column(name = "parent_id")
    UUID parentId;

    @Column(name = "issued_at", nullable = false)
    Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    Instant expiresAt;

    @Column(name = "used_at")
    Instant usedAt;

    @Column(name = "revoked_at")
    Instant revokedAt;

    @Column(name = "revoked_reason", length = 32)
    String revokedReason;

    @Column(name = "user_agent", length = 512)
    String userAgent;

    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "ip")
    String ip;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }
}
