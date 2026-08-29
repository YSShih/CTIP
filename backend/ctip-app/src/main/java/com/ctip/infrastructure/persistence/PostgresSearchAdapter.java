package com.ctip.infrastructure.persistence;

import com.ctip.application.port.SearchBackend;
import com.ctip.application.port.SearchPort;
import com.ctip.application.port.SearchQuery;
import com.ctip.application.port.SearchResult;
import com.ctip.domain.indicator.Indicator;
import com.ctip.domain.shared.Cursor;
import com.ctip.domain.shared.CursorPage;
import com.ctip.infrastructure.security.TlpSpecifications;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * M1 的 PostgreSQL 搜尋(docs/spec/13-platform-ops.md §13.7 的 PostgresSearchAdapter),
 * M2 起同時是 Elasticsearch 不可用時的降級目標({@code FallbackSearchAdapter})。
 * normalized_value 子字串比對(pg_trgm 索引輔助),可見度與過濾條件與清單查詢共用同一套 Specification。
 *
 * <p>{@code fuzzy} 在此無效:§13.7 明定模糊查詢僅 M2(Elasticsearch)。降級時呼叫端由
 * {@code X-Search-Backend: postgres} 得知這件事,而不是靜默地拿到不同語意的結果。
 */
@Component
@Transactional(readOnly = true)
class PostgresSearchAdapter implements SearchPort {

    private static final Sort CURSOR_SORT = Sort.by(Sort.Order.desc("lastSeen"), Sort.Order.desc("id"));

    private final IndicatorJpaRepository jpa;
    private final IndicatorMapper mapper;

    PostgresSearchAdapter(IndicatorJpaRepository jpa, IndicatorMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public SearchResult search(SearchQuery query) {
        String needle = "%" + escapeLike(query.term().toLowerCase(Locale.ROOT)) + "%";
        Specification<IndicatorEntity> spec = Specification.allOf(
                (root, criteria, cb) -> cb.like(root.get("normalizedValue"), needle, '\\'),
                TlpSpecifications.visibleTo(query.visibility()),
                IndicatorFilterSpecs.matches(query.filter(), query.visibility()));
        Cursor after = query.after();
        if (after != null) {
            spec = spec.and((root, criteria, cb) -> cb.or(
                    cb.lessThan(root.get("lastSeen"), after.lastSeen()),
                    cb.and(cb.equal(root.get("lastSeen"), after.lastSeen()), cb.lessThan(root.get("id"), after.id()))));
        }
        int limit = query.limit();
        List<IndicatorEntity> rows =
                jpa.findBy(spec, q -> q.sortBy(CURSOR_SORT).limit(limit + 1).all());
        boolean hasMore = rows.size() > limit;
        List<IndicatorEntity> page = hasMore ? rows.subList(0, limit) : rows;
        String nextCursor = hasMore ? new Cursor(page.getLast().lastSeen, page.getLast().id).encode() : null;
        CursorPage<Indicator> result =
                new CursorPage<>(page.stream().map(mapper::toDomain).toList(), nextCursor, hasMore);
        return new SearchResult(result, SearchBackend.POSTGRES);
    }

    /** LIKE 萬用字元跳脫:使用者輸入的 % _ \ 視為字面值。 */
    private static String escapeLike(String term) {
        return term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
