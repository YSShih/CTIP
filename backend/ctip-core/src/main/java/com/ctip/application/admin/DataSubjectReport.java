package com.ctip.application.admin;

import com.ctip.application.audit.AuditActorSummary;
import java.time.Instant;
import java.util.UUID;

/**
 * 資料主體查詢的結果(docs/spec/13-platform-ops.md §13.4):平台持有哪些關於這個人的資料。
 *
 * @param activeRefreshTokens 尚未撤銷的 refresh token 數(每一列都帶 ip 與 user-agent)
 * @param auditTrail 稽核軌跡的筆數與時間範圍;內容不在此回傳(可能牽涉其他人的操作)
 */
public record DataSubjectReport(
        UUID userId,
        String email,
        String displayName,
        String status,
        Instant lastLoginAt,
        int activeRefreshTokens,
        AuditActorSummary auditTrail) {}
