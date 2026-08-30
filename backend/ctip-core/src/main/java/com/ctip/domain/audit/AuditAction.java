package com.ctip.domain.audit;

/**
 * 稽核行為(docs/spec/04-data-dictionary.md §4.5「稽核行為」,26 種;
 * 觸發點見 docs/spec/13-platform-ops.md §13.5 觸發點對照表)。
 *
 * <p>每一個值都必須有實際的寫入路徑——{@code AuditCompletenessTest} 逐一驗證,
 * 不得有永不可達的行為(00-master.md 執行規則 16)。
 */
public enum AuditAction {
    LOGIN,
    LOGIN_FAILED,
    LOGOUT,
    TOKEN_REFRESH,
    TOKEN_REUSE_DETECTED,
    API_ACCESS,
    IOC_QUERY,
    IOC_DOWNLOAD,
    IOC_SUBMIT,
    IOC_IMPORT,
    IOC_REPORT_FP,
    STIX_EXPORT,
    SYNC_MANIFEST,
    SYNC_BLOOM,
    SYNC_DELTA,
    INGESTION_STARTED,
    INGESTION_COMPLETED,
    INGESTION_FAILED,
    ADMIN_ACTION,
    TENANT_CREATED,
    USER_CREATED,
    API_KEY_CREATED,
    API_KEY_REVOKED,
    SUBSCRIPTION_CHANGED,
    WEBHOOK_CREATED,
    WEBHOOK_DELETED
}
