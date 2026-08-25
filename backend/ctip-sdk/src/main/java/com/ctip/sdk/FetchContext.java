package com.ctip.sdk;

import java.time.Instant;
import java.util.Map;

/**
 * 一次抓取的輸入(docs/spec/08-ingestion-sdk.md §8.1)。
 *
 * @param since 上次成功同步時間,首次為 null
 * @param cursor 來源自訂的續抓游標,首次為 null
 * @param config 來自環境設定,含憑證(sources.config 只存環境變數名稱,解析後才進到這裡)
 * @param maxRecords 本次抓取的筆數上限
 */
public record FetchContext(Instant since, String cursor, Map<String, String> config, int maxRecords) {}
