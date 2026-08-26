package com.ctip.interfaces.rest.dto.ioc;

import com.ctip.domain.indicator.IndicatorStatus;
import com.ctip.sdk.IocType;
import com.ctip.sdk.Severity;
import com.ctip.sdk.Tlp;

/** GET /iocs 的查詢參數(§9.1 篩選 + §9.3 分頁);由 Spring 以建構子繫結。 */
public record IocListParams(
        String cursor,
        Integer offset,
        Integer limit,
        IocType type,
        Severity severity,
        IndicatorStatus status,
        Tlp tlp,
        Boolean includeExpired) {

    public boolean includeExpiredOrDefault() {
        return Boolean.TRUE.equals(includeExpired);
    }
}
