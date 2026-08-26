package com.ctip.application.port;

import com.ctip.application.indicator.IndicatorFilter;
import com.ctip.domain.indicator.Indicator;
import com.ctip.domain.shared.Cursor;
import com.ctip.domain.shared.CursorPage;
import com.ctip.domain.shared.Visibility;

/**
 * 情資搜尋(M1 = PostgreSQL、M2 = Elasticsearch + 降級;docs/spec/13-platform-ops.md §13.7)。
 * 回傳 domain 自有的 CursorPage,不使用 Spring Data Page(ArchUnit 規則 8)。
 */
public interface SearchPort {

    CursorPage<Indicator> searchByValue(
            String term, IndicatorFilter filter, Visibility visibility, Cursor after, int limit);
}
