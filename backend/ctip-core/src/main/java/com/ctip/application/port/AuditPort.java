package com.ctip.application.port;

import com.ctip.application.audit.AuditEvent;

/**
 * 稽核寫入(docs/spec/13-platform-ops.md §13.5)。
 *
 * <p><strong>實作必須是非同步且不得丟出例外</strong>(規則 3:稽核寫入失敗不得使主要業務操作失敗)。
 * 呼叫端因此永遠不需要 try/catch,也不需要判斷回傳值——沒有回傳值可判斷。
 */
public interface AuditPort {

    void record(AuditEvent event);
}
