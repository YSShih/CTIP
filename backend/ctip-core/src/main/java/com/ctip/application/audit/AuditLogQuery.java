package com.ctip.application.audit;

import com.ctip.domain.audit.AuditAction;
import com.ctip.domain.shared.Cursor;
import com.ctip.domain.tenant.TenantId;

/**
 * {@code GET /api/v1/audit-logs} 的查詢條件(docs/spec/09-api.md §9.1「通知與稽核」)。
 *
 * <p>租戶範圍由呼叫端的身分決定,不接受呼叫端指定——沒有「看別的租戶的稽核軌跡」這件事。
 *
 * @param action null = 不篩行為
 */
public record AuditLogQuery(TenantId tenantId, AuditAction action, Cursor cursor, int limit) {}
