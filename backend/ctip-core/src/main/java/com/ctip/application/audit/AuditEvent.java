package com.ctip.application.audit;

import com.ctip.domain.audit.AuditAction;
import com.ctip.domain.audit.AuditActorType;
import com.ctip.domain.audit.AuditResult;
import com.ctip.domain.tenant.TenantId;
import java.util.Map;
import java.util.UUID;

/**
 * 呼叫端交出的稽核事實(docs/spec/13-platform-ops.md §13.5)。
 *
 * <p>不含 {@code id}、{@code occurredAt}、{@code ip}、{@code userAgent}、{@code traceId}:
 * 那些是環境資訊,由 {@code AuditPort} 的實作在寫入端補齊——請求路徑取自當前請求,
 * 背景任務則留白。呼叫端只描述「誰、對什麼、做了什麼、結果如何」。
 *
 * @param tenantId null = 由寫入端以當前請求的租戶補齊(背景任務必須自行指定)
 * @param actorType null = 同上
 */
public record AuditEvent(
        AuditAction action,
        AuditResult result,
        AuditActorType actorType,
        UUID actorId,
        TenantId tenantId,
        String resourceType,
        UUID resourceId,
        Map<String, Object> metadata) {

    public AuditEvent {
        if (action == null || result == null) {
            throw new IllegalArgumentException("稽核事件必須有 action 與 result");
        }
        metadata = AuditMetadata.sanitize(metadata);
    }

    /** 行為者與租戶由寫入端依當前請求補齊。 */
    public static AuditEvent of(AuditAction action, AuditResult result) {
        return new AuditEvent(action, result, null, null, null, null, null, Map.of());
    }

    /** 背景任務(排程、事件 listener)沒有請求上下文,行為者為系統。 */
    public static AuditEvent system(AuditAction action, AuditResult result, TenantId tenantId) {
        return new AuditEvent(action, result, AuditActorType.SYSTEM, null, tenantId, null, null, Map.of());
    }

    public AuditEvent withActor(AuditActorType type, UUID id) {
        return new AuditEvent(action, result, type, id, tenantId, resourceType, resourceId, metadata);
    }

    public AuditEvent withTenant(TenantId tenant) {
        return new AuditEvent(action, result, actorType, actorId, tenant, resourceType, resourceId, metadata);
    }

    public AuditEvent withResource(String type, UUID id) {
        return new AuditEvent(action, result, actorType, actorId, tenantId, type, id, metadata);
    }

    public AuditEvent withMetadata(Map<String, Object> entries) {
        return new AuditEvent(action, result, actorType, actorId, tenantId, resourceType, resourceId, entries);
    }
}
