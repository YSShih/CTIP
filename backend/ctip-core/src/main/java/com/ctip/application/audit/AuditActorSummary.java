package com.ctip.application.audit;

import java.time.Instant;

/**
 * 某個行為者在稽核軌跡中的足跡摘要(資料主體查詢;docs/spec/13-platform-ops.md §13.4)。
 *
 * <p>只回筆數與時間範圍,不回內容:資料主體查詢的目的是「平台持有哪些關於我的資料」,
 * 而稽核內容本身可能牽涉其他人的操作。
 */
public record AuditActorSummary(long count, Instant earliest, Instant latest) {

    public static AuditActorSummary empty() {
        return new AuditActorSummary(0, null, null);
    }
}
