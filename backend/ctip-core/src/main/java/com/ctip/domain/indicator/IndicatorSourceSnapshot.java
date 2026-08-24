package com.ctip.domain.indicator;

import com.ctip.domain.source.SourceId;
import com.ctip.sdk.Confidence;
import com.ctip.sdk.RedistributionPolicy;
import com.ctip.sdk.Severity;
import com.ctip.sdk.Tlp;
import java.time.Instant;
import java.util.Set;

/**
 * 來源記錄的持久化快照。tags 為合併輸入(I9),只物化於 indicators.tags 的聯集,
 * 不隨 indicator_sources 持久化——重建時為空集合。
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
        Set<String> tags) {}
