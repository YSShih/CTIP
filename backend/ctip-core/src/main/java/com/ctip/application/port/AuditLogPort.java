package com.ctip.application.port;

import com.ctip.application.audit.AuditActorSummary;
import com.ctip.application.audit.AuditLogQuery;
import com.ctip.application.audit.AuditRecord;
import com.ctip.domain.shared.CursorPage;
import java.util.List;
import java.util.UUID;

/**
 * {@code audit_logs} 的持久化 port(兩模型表,無 domain model)。
 *
 * <p>只有 append 與查詢——沒有 update、沒有 delete。這一點在資料庫層也成立
 * (V33 的 {@code REVOKE UPDATE, DELETE};§13.5 規則 1),保留清理走另一個 DB 角色。
 *
 * <p>業務程式碼<strong>不呼叫本介面</strong>,而是呼叫 {@link AuditPort}:後者非同步且不丟例外
 * (§13.5 規則 3)。本介面是它背後的同步寫入面。
 */
public interface AuditLogPort {

    /** 批次寫入;呼叫端(稽核寫入執行緒)自行處理例外。 */
    void append(List<AuditRecord> records);

    CursorPage<AuditRecord> list(AuditLogQuery query);

    /** 某個行為者留下的稽核軌跡摘要(資料主體查詢;13 §13.4)。 */
    AuditActorSummary summarizeActor(UUID actorId);
}
