package com.ctip.sdk;

/**
 * 來源型別,對應 sources.source_type 與 adapter 註冊鍵(docs/spec/08-ingestion-sdk.md §8.1)。
 * 新增成員為 SDK minor 變更;所有 switch 必須 exhaustive(docs/spec/02-ddd-model.md §2.5)。
 */
public enum SourceType {
    MOCK_OPENPHISH,
    MOCK_ABUSEIPDB,
    MOCK_ALIENVAULT,
    MANUAL
}
