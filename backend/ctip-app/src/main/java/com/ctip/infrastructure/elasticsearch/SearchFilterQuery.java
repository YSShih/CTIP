package com.ctip.infrastructure.elasticsearch;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.json.JsonData;
import com.ctip.application.indicator.IndicatorFilter;
import com.ctip.application.indicator.IntRange;
import com.ctip.application.indicator.TimeRange;
import com.ctip.domain.indicator.IndicatorStatus;
import com.ctip.domain.shared.Visibility;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link IndicatorFilter} 在 Elasticsearch 上的重建——{@code IndicatorFilterSpecs} 的對應實作
 * (docs/spec/13-platform-ops.md §13.7 搜尋欄位、09 §9.5 輸出過濾第 3 步)。
 *
 * <p>⚠️ {@code sourceId} 帶著 ADR 0015 修正 2 的揭露規則:輸出遮蔽了來源明細,查詢就不得能用
 * 該來源過濾,否則逐一試 {@code sourceId} 即可還原被遮蔽的歸屬。索引預先算好
 * {@code disclosableSourceIds},查詢時只在「viewer 是擁有租戶(非 public)」時才放行完整的
 * {@code sourceIds}。
 */
final class SearchFilterQuery {

    private SearchFilterQuery() {}

    static List<Query> of(IndicatorFilter filter, Visibility visibility) {
        List<Query> clauses = new ArrayList<>();
        addEnums(filter, clauses);
        for (String tag : filter.tags()) {
            clauses.add(QueryBuilders.term(t -> t.field(SearchFields.TAGS).value(tag)));
        }
        if (filter.sourceId() != null) {
            clauses.add(reportedBySource(filter, visibility));
        }
        addRanges(filter, clauses);
        return clauses;
    }

    private static void addEnums(IndicatorFilter filter, List<Query> clauses) {
        if (filter.type() != null) {
            clauses.add(term(SearchFields.TYPE, filter.type().name()));
        }
        if (filter.severity() != null) {
            clauses.add(term(SearchFields.SEVERITY, filter.severity().name()));
        }
        if (filter.tlp() != null) {
            clauses.add(term(SearchFields.TLP, filter.tlp().name()));
        }
        if (filter.status() != null) {
            clauses.add(term(SearchFields.STATUS, filter.status().name()));
        } else if (filter.excludesExpiredByDefault()) {
            clauses.add(QueryBuilders.bool(b -> b.mustNot(term(SearchFields.STATUS, IndicatorStatus.EXPIRED.name()))));
        }
    }

    private static void addRanges(IndicatorFilter filter, List<Query> clauses) {
        intRange(SearchFields.CONFIDENCE, filter.confidence(), clauses);
        intRange(SearchFields.SCORE, filter.score(), clauses);
        timeRange(SearchFields.LAST_SEEN, filter.lastSeen(), clauses);
    }

    private static void intRange(String field, IntRange range, List<Query> clauses) {
        if (range.isUnbounded()) {
            return;
        }
        clauses.add(QueryBuilders.range(r -> r.untyped(u -> {
            u.field(field);
            if (range.min() != null) {
                u.gte(JsonData.of(range.min()));
            }
            if (range.max() != null) {
                u.lte(JsonData.of(range.max()));
            }
            return u;
        })));
    }

    private static void timeRange(String field, TimeRange range, List<Query> clauses) {
        if (range.isUnbounded()) {
            return;
        }
        clauses.add(QueryBuilders.range(r -> r.untyped(u -> {
            u.field(field);
            if (range.from() != null) {
                u.gte(JsonData.of(iso(range.from())));
            }
            if (range.to() != null) {
                u.lte(JsonData.of(iso(range.to())));
            }
            return u;
        })));
    }

    private static Query reportedBySource(IndicatorFilter filter, Visibility visibility) {
        String sourceId = filter.sourceId().toString();
        Query disclosable = term(SearchFields.DISCLOSABLE_SOURCE_IDS, sourceId);
        if (visibility.viewerTenantId().isPublic()) {
            return disclosable;
        }
        Query ownRecord = QueryBuilders.bool(b -> b.filter(
                SearchVisibilityQuery.owner(visibility.viewerTenantId()), term(SearchFields.SOURCE_IDS, sourceId)));
        return QueryBuilders.bool(b -> b.should(ownRecord, disclosable).minimumShouldMatch("1"));
    }

    private static Query term(String field, String value) {
        return QueryBuilders.term(t -> t.field(field).value(value));
    }

    private static String iso(Instant instant) {
        return instant.toString();
    }
}
