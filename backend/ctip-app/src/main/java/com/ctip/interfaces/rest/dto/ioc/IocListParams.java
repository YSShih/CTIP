package com.ctip.interfaces.rest.dto.ioc;

import com.ctip.domain.indicator.IndicatorStatus;
import com.ctip.sdk.IocType;
import com.ctip.sdk.Severity;
import com.ctip.sdk.Tlp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * GET /iocs 的查詢參數(§9.1 篩選 + §9.3 分頁 + 13 §13.7 搜尋欄位);由 Spring 以建構子繫結。
 * tags 為重複參數(?tags=a&amp;tags=b),語意為全部包含;lastSeenFrom/To 為 ISO-8601 instant。
 */
public record IocListParams(
        String cursor,
        Integer offset,
        Integer limit,
        IocType type,
        Severity severity,
        IndicatorStatus status,
        Tlp tlp,
        Boolean includeExpired,
        List<String> tags,
        UUID sourceId,
        Integer confidenceMin,
        Integer confidenceMax,
        Integer scoreMin,
        Integer scoreMax,
        Instant lastSeenFrom,
        Instant lastSeenTo) {

    public boolean includeExpiredOrDefault() {
        return Boolean.TRUE.equals(includeExpired);
    }
}
