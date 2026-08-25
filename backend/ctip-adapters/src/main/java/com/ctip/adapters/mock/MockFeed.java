package com.ctip.adapters.mock;

import com.ctip.sdk.FetchContext;
import com.ctip.sdk.FetchResult;
import com.ctip.sdk.IocHashType;
import com.ctip.sdk.IocType;
import com.ctip.sdk.RawThreatRecord;
import com.ctip.sdk.Severity;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 三個 mock adapter 共用的確定性分頁與記錄建構(docs/spec/08-ingestion-sdk.md §8.3)。
 * 資料集為手寫固定清單——不使用任何 Random,同一 FetchContext 必然回傳 equals 的結果。
 * cursor 為資料集內的 offset(十進位字串)。
 */
final class MockFeed {

    private MockFeed() {}

    /** since 過濾 + offset 分頁;maxRecords 為本次上限,pageSize 為來源自身的頁大小。 */
    static FetchResult page(List<RawThreatRecord> dataset, FetchContext context, int pageSize) {
        if (context.maxRecords() <= 0) {
            throw new IllegalArgumentException("maxRecords 必須為正數:" + context.maxRecords());
        }
        List<RawThreatRecord> visible = context.since() == null
                ? dataset
                : dataset.stream()
                        .filter(r -> r.observedAt().isAfter(context.since()))
                        .toList();
        int offset = context.cursor() == null ? 0 : Integer.parseInt(context.cursor());
        int limit = Math.min(pageSize, context.maxRecords());
        int end = Math.min(visible.size(), Math.addExact(offset, limit));
        List<RawThreatRecord> records = offset >= visible.size() ? List.of() : visible.subList(offset, end);
        boolean hasMore = end < visible.size();
        return new FetchResult(List.copyOf(records), hasMore ? String.valueOf(end) : null, hasMore);
    }

    /** 一般 feed 記錄(無 STIX payload)。 */
    static RawThreatRecord record(
            String rawValue, IocType declaredType, String observedAt, Integer confidence, Severity severity) {
        return new RawThreatRecord(
                rawValue,
                declaredType,
                null,
                Instant.parse(observedAt),
                confidence,
                severity,
                null,
                Set.of(),
                Map.of());
    }

    /** 附標籤的 feed 記錄。 */
    static RawThreatRecord tagged(
            String rawValue, IocType declaredType, String observedAt, Integer confidence, Set<String> tags) {
        return new RawThreatRecord(
                rawValue, declaredType, null, Instant.parse(observedAt), confidence, null, null, tags, Map.of());
    }

    /** FILE_HASH 記錄(宣告雜湊演算法)。 */
    static RawThreatRecord hash(
            String rawValue, IocHashType declaredHashType, String observedAt, Integer confidence, Severity severity) {
        return new RawThreatRecord(
                rawValue,
                IocType.FILE_HASH,
                declaredHashType,
                Instant.parse(observedAt),
                confidence,
                severity,
                null,
                Set.of(),
                Map.of());
    }

    /** STIX 風格記錄(MockAlienVault)。 */
    static RawThreatRecord stix(
            String rawValue, IocType declaredType, String observedAt, Integer confidence, Severity severity) {
        return new RawThreatRecord(
                rawValue,
                declaredType,
                null,
                Instant.parse(observedAt),
                confidence,
                severity,
                null,
                Set.of(),
                stixPayload(false));
    }

    /** STIX 風格撤回記錄:revoked = true(STIX 2.1 的 revoked 欄位)。 */
    static RawThreatRecord stixRevoked(
            String rawValue, IocType declaredType, String observedAt, Integer confidence, Severity severity) {
        return new RawThreatRecord(
                rawValue,
                declaredType,
                null,
                Instant.parse(observedAt),
                confidence,
                severity,
                null,
                Set.of(),
                stixPayload(true));
    }

    private static Map<String, Object> stixPayload(boolean revoked) {
        return Map.of("type", "indicator", "spec_version", "2.1", "pattern_type", "stix", "revoked", revoked);
    }
}
