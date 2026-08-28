package com.ctip.domain.indicator;

import com.ctip.domain.source.SourceId;
import com.ctip.sdk.Confidence;
import com.ctip.sdk.RedistributionPolicy;
import com.ctip.sdk.Severity;
import com.ctip.sdk.Tlp;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * 來源記錄的持久化快照。tags 為合併輸入(I9),只物化於 indicators.tags 的聯集,
 * 不隨 indicator_sources 持久化——重建時為空集合。
 *
 * <p>{@code rawPayload} 對應 {@code indicator_sources.raw_payload}(JSONB),承載來源原始 payload
 * 與手動提交的 {@code note}。它是<strong>只寫不讀</strong>的:聚合本身不解讀它
 * (解讀在 {@code ParseStage},輸入來自 {@code RawThreatRecord}),故重建時為空 Map,
 * 而持久化層只在新快照帶有內容時覆寫——否則重建後的合併會把既有 payload 抹成 null(ADR 0023)。
 */
public record IndicatorSourceSnapshot(
        SourceId sourceId,
        String sourceValue,
        Confidence sourceConfidence,
        Severity sourceSeverity,
        Tlp sourceTlp,
        Instant sourceFirstSeen,
        Instant sourceLastSeen,
        Instant sourceValidUntil,
        RedistributionPolicy redistributionPolicy,
        int reportCount,
        SourceRecordStatus status,
        Set<String> tags,
        Map<String, Object> rawPayload) {

    public IndicatorSourceSnapshot {
        tags = tags == null ? Set.of() : Set.copyOf(tags);
        rawPayload = rawPayload == null ? Map.of() : Map.copyOf(rawPayload);
    }
}
