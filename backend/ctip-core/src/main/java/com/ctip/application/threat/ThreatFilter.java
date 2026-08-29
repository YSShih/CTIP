package com.ctip.application.threat;

import com.ctip.domain.threat.ThreatStatus;
import com.ctip.domain.threat.ThreatType;
import com.ctip.sdk.Severity;
import com.ctip.sdk.Tlp;
import java.util.List;

/**
 * Threat 清單的查詢條件(docs/spec/09-api.md §9.1)。各欄位 null / 空表示不過濾。
 *
 * <p>{@code tags} 與 {@code aliases} 都是「全部包含」語意(text[] 的 {@code @>},
 * 吃 {@code ix_threats_aliases} 的 GIN 索引);{@code name} 為子字串比對(大小寫不敏感)。
 * 未指定 status 時預設排除 RETIRED——與 IOC 清單預設排除 EXPIRED 同一種「預設不看歷史」語意。
 */
public record ThreatFilter(
        ThreatType type,
        ThreatStatus status,
        Severity severity,
        Tlp tlp,
        boolean includeRetired,
        String name,
        List<String> tags,
        List<String> aliases) {

    private static final ThreatFilter NONE = new ThreatFilter(null, null, null, null, false, null, null, null);

    public ThreatFilter {
        tags = tags == null ? List.of() : List.copyOf(tags);
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
        name = name == null || name.isBlank() ? null : name.trim();
    }

    public static ThreatFilter none() {
        return NONE;
    }

    /** 是否需要「預設排除 RETIRED」的隱含條件。 */
    public boolean excludesRetiredByDefault() {
        return status == null && !includeRetired;
    }
}
