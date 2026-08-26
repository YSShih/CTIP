package com.ctip.infrastructure.persistence;

import com.ctip.application.indicator.IndicatorFilter;
import com.ctip.application.port.SearchPort;
import com.ctip.domain.indicator.Indicator;
import com.ctip.domain.shared.Cursor;
import com.ctip.domain.shared.CursorPage;
import com.ctip.domain.shared.Visibility;
import com.ctip.infrastructure.security.TlpSpecifications;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * M1 的 PostgreSQL 搜尋(docs/spec/13-platform-ops.md §13.7:M2 换 Elasticsearch + 降級,
 * 同一 port)。normalized_value 子字串比對(pg_trgm 索引輔助),可見度與過濾條件
 * 與清單查詢共用同一套 Specification。
 */
@Component
@Transactional(readOnly = true)
class SearchAdapter implements SearchPort {

    private static final Sort CURSOR_SORT = Sort.by(Sort.Order.desc("lastSeen"), Sort.Order.desc("id"));

    private final IndicatorJpaRepository jpa;
    private final IndicatorMapper mapper;

    SearchAdapter(IndicatorJpaRepository jpa, IndicatorMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public CursorPage<Indicator> searchByValue(
            String term, IndicatorFilter filter, Visibility visibility, Cursor after, int limit) {
        String needle = "%" + escapeLike(term.toLowerCase(Locale.ROOT)) + "%";
        Specification<IndicatorEntity> spec = Specification.allOf(
                (root, query, cb) -> cb.like(root.get("normalizedValue"), needle, '\\'),
                TlpSpecifications.visibleTo(visibility),
                IndicatorFilterSpecs.matches(filter));
        if (after != null) {
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.lessThan(root.get("lastSeen"), after.lastSeen()),
                    cb.and(cb.equal(root.get("lastSeen"), after.lastSeen()), cb.lessThan(root.get("id"), after.id()))));
        }
        List<IndicatorEntity> rows =
                jpa.findBy(spec, q -> q.sortBy(CURSOR_SORT).limit(limit + 1).all());
        boolean hasMore = rows.size() > limit;
        List<IndicatorEntity> page = hasMore ? rows.subList(0, limit) : rows;
        String nextCursor = hasMore ? new Cursor(page.getLast().lastSeen, page.getLast().id).encode() : null;
        return new CursorPage<>(page.stream().map(mapper::toDomain).toList(), nextCursor, hasMore);
    }

    /** LIKE 萬用字元跳脫:使用者輸入的 % _ \ 視為字面值。 */
    private static String escapeLike(String term) {
        return term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
