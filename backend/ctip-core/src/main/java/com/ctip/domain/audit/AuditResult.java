package com.ctip.domain.audit;

/**
 * 稽核紀錄的結果欄位(docs/spec/13-platform-ops.md §13.5):
 * 操作成功 → {@code SUCCESS};業務失敗 → {@code FAILURE};權限或配額拒絕 → {@code DENIED}。
 */
public enum AuditResult {
    SUCCESS,
    FAILURE,
    DENIED
}
