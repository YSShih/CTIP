package com.ctip.application.indicator;

import com.ctip.domain.indicator.IndicatorStatus;
import com.ctip.sdk.IocType;
import com.ctip.sdk.Severity;
import com.ctip.sdk.Tlp;
import java.util.List;
import java.util.UUID;

/**
 * IOC 清單/搜尋的查詢條件(docs/spec/09-api.md §9.1、§9.5;13 §13.7 搜尋欄位)。
 * 各欄位 null / 空表示不過濾;status 過濾規則:未指定 status 且 includeExpired=false 時
 * 排除 EXPIRED(§9.5 輸出過濾第 3 步);明確指定 status 時以指定值為準。
 * tags 為「全部包含」語意(indicators.tags @> 指定集合,吃 GIN 索引);
 * sourceId 過濾曾由該來源回報的 indicator;confidence/score/lastSeen 為閉區間。
 */
public record IndicatorFilter(
        IocType type,
        Severity severity,
        IndicatorStatus status,
        Tlp tlp,
        boolean includeExpired,
        List<String> tags,
        UUID sourceId,
        IntRange confidence,
        IntRange score,
        TimeRange lastSeen) {

    private static final IndicatorFilter NONE = new IndicatorFilter(null, null, null, null, false);

    public IndicatorFilter {
        tags = tags == null ? List.of() : List.copyOf(tags);
        confidence = confidence == null ? IntRange.unbounded() : confidence;
        score = score == null ? IntRange.unbounded() : score;
        lastSeen = lastSeen == null ? TimeRange.unbounded() : lastSeen;
    }

    /** 僅基本欄位的條件(Phase 9 介面;其餘欄位不過濾)。 */
    public IndicatorFilter(IocType type, Severity severity, IndicatorStatus status, Tlp tlp, boolean includeExpired) {
        this(type, severity, status, tlp, includeExpired, null, null, null, null, null);
    }

    public static IndicatorFilter none() {
        return NONE;
    }

    /** 是否需要「預設排除 EXPIRED」的隱含條件。 */
    public boolean excludesExpiredByDefault() {
        return status == null && !includeExpired;
    }
}
