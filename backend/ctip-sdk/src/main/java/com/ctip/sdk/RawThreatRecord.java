package com.ctip.sdk;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * 來源回報的原始情資記錄(docs/spec/08-ingestion-sdk.md §8.1)。
 * declaredHashType 與 sourceSeverity 是 indicator_sources 的必要輸入;
 * validUntil 僅在來源明示時非 null——它是三步過期計算(§4.6)區分
 * 「來源說永不過期」與「來源沒說」的唯一依據。
 *
 * @param rawValue 原始值,平台正規化前的樣貌
 * @param declaredType 來源宣告的型別,可為 null 由平台推斷
 * @param declaredHashType 僅 FILE_HASH 有意義,可為 null
 * @param observedAt 來源觀測到此 IOC 的時間
 * @param sourceConfidence 0-100,可為 null
 * @param sourceSeverity 可為 null
 * @param validUntil 僅在來源明示時非 null
 * @param tags 來源附帶的標籤
 * @param rawPayload 來源原始 payload(STIX 風格來源以 {@code revoked=true} 表達撤回)
 */
public record RawThreatRecord(
        String rawValue,
        IocType declaredType,
        IocHashType declaredHashType,
        Instant observedAt,
        Integer sourceConfidence,
        Severity sourceSeverity,
        Instant validUntil,
        Set<String> tags,
        Map<String, Object> rawPayload) {}
