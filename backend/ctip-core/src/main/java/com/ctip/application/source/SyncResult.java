package com.ctip.application.source;

/** source_sync.result(docs/spec/04-data-dictionary.md 表 3)。RUNNING 為 start 時的初值。 */
public enum SyncResult {
    RUNNING,
    SUCCESS,
    /** 執行完成但有記錄被拒絕。 */
    PARTIAL,
    FAILURE
}
