package com.ctip.application.port;

import com.ctip.application.indicator.IndicatorFilter;
import com.ctip.domain.shared.Cursor;
import com.ctip.domain.shared.Visibility;
import java.util.Objects;

/**
 * 一次搜尋的完整輸入(docs/spec/13-platform-ops.md §13.7)。
 *
 * <p>包成 record 而非攤平為參數有兩個理由:{@code fuzzy} 加上去會使原本已有 5 個參數的簽章
 * 超過 checkstyle 的 {@code ParameterNumber ≤ 5}(docs/spec/01-architecture.md §1.8);
 * 而且形狀正好回到 §13.7 原型的 {@code search(query, cursor, limit)}。
 *
 * <p>{@code visibility} 是查詢<strong>輸入</strong>而非事後過濾(§1.11 強制);
 * {@code fuzzy} 是 M2 才有的能力(§13.7「模糊查詢(僅 M2),用於 typosquatting 偵測」),
 * PostgreSQL 後端服務這次查詢時它無效——回應的 {@code X-Search-Backend} 即為告知管道。
 */
public record SearchQuery(
        String term, boolean fuzzy, IndicatorFilter filter, Visibility visibility, Cursor after, int limit) {

    public SearchQuery {
        Objects.requireNonNull(term, "term");
        Objects.requireNonNull(filter, "filter");
        Objects.requireNonNull(visibility, "visibility");
    }
}
