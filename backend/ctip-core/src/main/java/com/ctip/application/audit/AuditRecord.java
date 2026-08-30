package com.ctip.application.audit;

import com.ctip.domain.audit.AuditAction;
import com.ctip.domain.audit.AuditActorType;
import com.ctip.domain.audit.AuditResult;
import com.ctip.domain.tenant.TenantId;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * {@code audit_logs} 的一列(docs/spec/04-data-dictionary.md 表 27)。
 * 該表是<strong>兩模型</strong>(01 §1.5:append-only 記錄,無跨欄位不變量),故沒有 domain model;
 * application 層以本 record 與 port 往來,JPA entity 留在 infrastructure。
 *
 * <p><strong>沒有 {@code updatedAt}</strong>——本表永不更新(§13.5 規則 6)。
 */
public record AuditRecord(
        UUID id,
        Instant occurredAt,
        AuditActorType actorType,
        UUID actorId,
        TenantId tenantId,
        AuditAction action,
        String resourceType,
        UUID resourceId,
        String ip,
        String userAgent,
        AuditResult result,
        String traceId,
        Map<String, Object> metadata) {}
