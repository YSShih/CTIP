package com.ctip.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * audit_logs(docs/spec/04-data-dictionary.md 表 27;兩模型、append-only)。
 *
 * <p><strong>刻意沒有 {@code updatedAt} 欄位</strong>——本表永不更新(§13.5 規則 6);
 * 應用角色的 UPDATE/DELETE 在 V33 已由 DB 收回。
 */
@Entity
@Table(name = "audit_logs")
class AuditLogEntity {

    @Id
    UUID id;

    @Column(name = "occurred_at", nullable = false)
    Instant occurredAt;

    @Column(name = "actor_type", nullable = false, length = 16)
    String actorType;

    @Column(name = "actor_id")
    UUID actorId;

    @Column(name = "tenant_id", nullable = false)
    UUID tenantId;

    @Column(nullable = false, length = 64)
    String action;

    @Column(name = "resource_type", length = 64)
    String resourceType;

    @Column(name = "resource_id")
    UUID resourceId;

    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "ip")
    String ip;

    @Column(name = "user_agent", length = 512)
    String userAgent;

    @Column(nullable = false, length = 16)
    String result;

    @Column(name = "trace_id", length = 64)
    String traceId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    String metadata;
}
