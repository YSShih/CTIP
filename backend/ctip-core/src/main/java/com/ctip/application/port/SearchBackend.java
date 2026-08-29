package com.ctip.application.port;

import java.util.Locale;

/**
 * 這次查詢實際由哪個後端服務(docs/spec/13-platform-ops.md §13.7)。
 *
 * <p>§13.7 要求 ES 不可用時降級並在回應帶 {@code X-Search-Backend},但同一節又禁止在 controller
 * 判斷降級——只有實際執行查詢的 {@code FallbackSearchAdapter} 知道結果,因此答案必須沿
 * {@link SearchPort} 的回傳值往上帶(ADR 0020 §8)。
 */
public enum SearchBackend {
    ELASTICSEARCH,
    POSTGRES;

    /** {@code X-Search-Backend} 的值:規格寫的是小寫 {@code elasticsearch|postgres}。 */
    public String headerValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
