package com.ctip.interfaces.rest.dto.admin;

import java.util.UUID;

/**
 * 資料主體刪除的結果(docs/spec/13-platform-ops.md §13.4)。
 *
 * @param retainedAuditEntries 仍保留的稽核列數——{@code audit_logs} 是 append-only 的
 *     (§13.5 規則 1),依 {@code AUDIT_RETENTION_DAYS} 自然到期,不由本操作刪除
 */
public record DataSubjectErasureDto(UUID userId, int deletedRefreshTokens, long retainedAuditEntries) {}
