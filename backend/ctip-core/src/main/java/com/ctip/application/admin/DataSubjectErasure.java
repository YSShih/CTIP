package com.ctip.application.admin;

import com.ctip.application.audit.AuditActorSummary;
import java.util.UUID;

/**
 * 資料主體刪除的結果。
 *
 * @param retainedAuditEntries 仍保留的稽核列數——它們是 append-only 的,
 *     依保留政策(180 天)自然到期,不由本操作刪除(§13.4、§13.5 規則 1)
 */
public record DataSubjectErasure(UUID userId, int deletedRefreshTokens, AuditActorSummary retainedAuditEntries) {}
