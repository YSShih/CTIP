package com.ctip.application.port;

import com.ctip.domain.indicator.Indicator;
import com.ctip.domain.shared.CursorPage;
import java.util.Objects;

/** 一次搜尋的結果與服務它的後端(後者供 {@code X-Search-Backend} 使用)。 */
public record SearchResult(CursorPage<Indicator> page, SearchBackend backend) {

    public SearchResult {
        Objects.requireNonNull(page, "page");
        Objects.requireNonNull(backend, "backend");
    }
}
