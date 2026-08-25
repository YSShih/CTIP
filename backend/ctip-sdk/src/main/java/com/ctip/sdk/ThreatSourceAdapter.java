package com.ctip.sdk;

/**
 * Plugin SDK 契約(docs/spec/08-ingestion-sdk.md §8.1)。
 * 第三方開發者實作 adapter 時只需依賴 ctip-sdk,不需修改核心 ingestion 邏輯;
 * 每個 {@link SourceType} 只能有一個實作(重複註冊於啟動時失敗)。
 */
public interface ThreatSourceAdapter {

    SourceType sourceType();

    SourceMetadata metadata();

    FetchResult fetch(FetchContext context);
}
