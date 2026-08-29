package com.ctip.interfaces.rest.dto.threat;

import com.ctip.domain.threat.ThreatStatus;
import com.ctip.domain.threat.ThreatType;
import com.ctip.sdk.Severity;
import com.ctip.sdk.Tlp;
import java.util.List;

/**
 * GET /threats 的查詢參數(§9.1 篩選 + §9.3 分頁);由 Spring 以建構子繫結。
 * tags / aliases 為重複參數(?tags=a&amp;tags=b),語意為全部包含;
 * name 為子字串比對;RETIRED 預設排除,除非 includeRetired=true 或明確指定 status。
 */
public record ThreatListParams(
        String cursor,
        Integer limit,
        ThreatType type,
        ThreatStatus status,
        Severity severity,
        Tlp tlp,
        Boolean includeRetired,
        String name,
        List<String> tags,
        List<String> aliases) {

    public boolean includeRetiredOrDefault() {
        return Boolean.TRUE.equals(includeRetired);
    }
}
