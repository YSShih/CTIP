package com.ctip.interfaces.rest.dto.admin;

import java.time.Instant;
import java.util.UUID;

/**
 * 資料主體查詢的結果(docs/spec/13-platform-ops.md §13.4)。
 *
 * @param auditEntries 稽核軌跡筆數;內容不在此回傳(可能牽涉其他人的操作)
 */
public record DataSubjectReportDto(
        UUID userId,
        String email,
        String displayName,
        String status,
        Instant lastLoginAt,
        int activeRefreshTokens,
        long auditEntries,
        Instant earliestAuditEntry,
        Instant latestAuditEntry) {}
