package com.ctip.application.indicator;

import com.ctip.domain.indicator.IndicatorStatus;
import com.ctip.sdk.IocType;
import com.ctip.sdk.Severity;
import com.ctip.sdk.Tlp;

/**
 * IOC 清單/搜尋的查詢條件(docs/spec/09-api.md §9.1、§9.5)。
 * 各欄位 null 表示不過濾;status 過濾規則:未指定 status 且 includeExpired=false 時
 * 排除 EXPIRED(§9.5 輸出過濾第 3 步);明確指定 status 時以指定值為準。
 */
public record IndicatorFilter(
        IocType type, Severity severity, IndicatorStatus status, Tlp tlp, boolean includeExpired) {

    private static final IndicatorFilter NONE = new IndicatorFilter(null, null, null, null, false);

    public static IndicatorFilter none() {
        return NONE;
    }

    /** 是否需要「預設排除 EXPIRED」的隱含條件。 */
    public boolean excludesExpiredByDefault() {
        return status == null && !includeExpired;
    }
}
