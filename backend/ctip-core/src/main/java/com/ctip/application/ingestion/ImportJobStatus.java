package com.ctip.application.ingestion;

/**
 * 匯入 job 狀態(docs/spec/04-data-dictionary.md 表 18b)。
 * {@code PARTIAL} = 有成功也有被拒的筆數;{@code FAILURE} = 整批失敗(解析錯誤等)。
 */
public enum ImportJobStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    PARTIAL,
    FAILURE;

    public boolean isTerminal() {
        return this == SUCCESS || this == PARTIAL || this == FAILURE;
    }
}
