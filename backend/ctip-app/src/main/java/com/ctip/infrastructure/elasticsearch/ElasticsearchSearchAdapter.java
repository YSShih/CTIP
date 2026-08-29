package com.ctip.infrastructure.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.ctip.application.port.IndicatorRepository;
import com.ctip.application.port.SearchBackend;
import com.ctip.application.port.SearchPort;
import com.ctip.application.port.SearchQuery;
import com.ctip.application.port.SearchResult;
import com.ctip.domain.indicator.Indicator;
import com.ctip.domain.indicator.IndicatorId;
import com.ctip.domain.shared.Cursor;
import com.ctip.domain.shared.CursorPage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * M2 的 Elasticsearch 搜尋(docs/spec/13-platform-ops.md §13.7)。
 *
 * <p><strong>ES 只回答「哪些 id、依什麼順序」</strong>;實際的 Indicator 一律由
 * {@link IndicatorRepository#findVisibleByIds} 從 PostgreSQL 取回。§13.7 明定 PostgreSQL 永遠是
 * source of truth,而這個作法額外買到一件事:即使索引落後或 mapping 有疏漏,
 * 不可見的資料也出不去——可見度在 source of truth 又被強制了一次(ADR 0028)。
 *
 * <p>可見度與過濾述詞在 ES 端仍必須完整重建({@link SearchVisibilityQuery}、
 * {@link SearchFilterQuery}):否則分頁與 {@code hasMore} 會建立在錯誤的候選集合上,
 * 而「本頁少了幾筆」本身就是一個側信道。
 *
 * <p>排序固定 {@code lastSeen DESC, id DESC},與 PostgreSQL 路徑逐字相同——降級可以發生在
 * 翻頁的任何一頁,兩邊的 cursor 必須可以互換。
 */
public class ElasticsearchSearchAdapter implements SearchPort {

    private final ElasticsearchClient client;
    private final IndicatorSearchIndex index;
    private final IndicatorRepository indicators;

    public ElasticsearchSearchAdapter(
            ElasticsearchClient client, IndicatorSearchIndex index, IndicatorRepository indicators) {
        this.client = client;
        this.index = index;
        this.indicators = indicators;
    }

    @Override
    public SearchResult search(SearchQuery query) {
        List<Hit<Void>> hits = execute(query);
        boolean hasMore = hits.size() > query.limit();
        List<Hit<Void>> page = hasMore ? hits.subList(0, query.limit()) : hits;
        List<Indicator> items = hydrate(page, query);
        String nextCursor = hasMore ? cursorOf(page.getLast()) : null;
        return new SearchResult(new CursorPage<>(items, nextCursor, hasMore), SearchBackend.ELASTICSEARCH);
    }

    private List<Hit<Void>> execute(SearchQuery query) {
        index.ensureExists();
        List<Query> filters = new ArrayList<>(SearchVisibilityQuery.of(query.visibility()));
        filters.addAll(SearchFilterQuery.of(query.filter(), query.visibility()));
        filters.add(SearchTermQuery.of(query.term(), query.fuzzy()));
        SearchRequest request = SearchRequest.of(s -> {
            s.index(IndicatorSearchIndex.NAME)
                    .query(q -> q.bool(b -> b.filter(filters)))
                    .source(source -> source.fetch(false))
                    .sort(sort -> sort.field(
                            f -> f.field(SearchFields.LAST_SEEN_NANOS).order(SortOrder.Desc)))
                    .sort(sort -> sort.field(f -> f.field(SearchFields.ID).order(SortOrder.Desc)))
                    .size(query.limit() + 1);
            if (query.after() != null) {
                s.searchAfter(
                        FieldValue.of(EpochNanos.of(query.after().lastSeen())),
                        FieldValue.of(query.after().id().toString()));
            }
            return s;
        });
        try {
            SearchResponse<Void> response = client.search(request, Void.class);
            return response.hits().hits();
        } catch (IOException | RuntimeException e) {
            throw new ElasticsearchQueryException("Elasticsearch 搜尋失敗", e);
        }
    }

    /**
     * 依 ES 給的順序從 source of truth 取回。查不到的(索引落後、已軟刪除、或對這個 viewer
     * 不可見)直接落空——本頁因此可能少於 limit,cursor 分頁允許短頁,而洩漏是不允許的。
     */
    private List<Indicator> hydrate(List<Hit<Void>> page, SearchQuery query) {
        if (page.isEmpty()) {
            return List.of();
        }
        List<IndicatorId> ids = page.stream()
                .map(hit -> new IndicatorId(UUID.fromString(hit.id())))
                .toList();
        Map<IndicatorId, Indicator> loaded = new LinkedHashMap<>();
        for (Indicator indicator : indicators.findVisibleByIds(ids, query.visibility())) {
            loaded.put(indicator.id(), indicator);
        }
        return ids.stream().map(loaded::get).filter(java.util.Objects::nonNull).toList();
    }

    /** cursor 取自 ES 的排序值,而非本頁最後一筆<strong>取回成功</strong>的資料——否則被過濾掉的尾端會被重複掃描。 */
    private static String cursorOf(Hit<Void> hit) {
        List<FieldValue> sort = hit.sort();
        long lastSeenNanos = sort.getFirst().longValue();
        return new Cursor(EpochNanos.toInstant(lastSeenNanos), UUID.fromString(hit.id())).encode();
    }
}
