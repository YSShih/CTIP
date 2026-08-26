package com.ctip.application.indicator;

import com.ctip.domain.indicator.Indicator;
import java.util.Objects;

/**
 * 批次精確驗證的單筆結果(docs/spec/09-api.md §9.1、11 §11.6):
 * found=false 涵蓋「查無」「不可見」「無法正規化」——對呼叫端一律等價於未命中,
 * 不洩漏資源存在性。
 */
public record LookupResult(String value, boolean found, Indicator indicator) {

    public LookupResult {
        Objects.requireNonNull(value, "value 不得為 null");
    }

    public static LookupResult miss(String value) {
        return new LookupResult(value, false, null);
    }

    public static LookupResult hit(String value, Indicator indicator) {
        return new LookupResult(value, true, indicator);
    }
}
