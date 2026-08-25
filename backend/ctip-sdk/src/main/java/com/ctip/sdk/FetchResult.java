package com.ctip.sdk;

import java.util.List;

/**
 * 一次抓取的輸出(docs/spec/08-ingestion-sdk.md §8.1)。
 * hasMore = true 時,呼叫端以 nextCursor 續抓;確定性 adapter 對同一 FetchContext 必須回傳 equals 的結果。
 */
public record FetchResult(List<RawThreatRecord> records, String nextCursor, boolean hasMore) {}
