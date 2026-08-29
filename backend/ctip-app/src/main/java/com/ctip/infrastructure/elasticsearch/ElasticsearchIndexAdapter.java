package com.ctip.infrastructure.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import com.ctip.application.port.IndexedDocument;
import com.ctip.application.port.SearchIndexDocument;
import com.ctip.application.port.SearchIndexPort;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 搜尋索引的寫入面(docs/spec/13-platform-ops.md §13.7)。文件 id 就是 indicator id,
 * 因此 bulk index 天然冪等——重跑同一批不會產生第二份。
 *
 * <p>refresh 一律不強制:索引是最終一致的讀取副本,而 reconciliation 每日對帳。
 * 需要立刻看得到的只有測試,由測試自己 refresh。
 */
public class ElasticsearchIndexAdapter implements SearchIndexPort {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchIndexAdapter.class);

    private final ElasticsearchClient client;
    private final IndicatorSearchIndex index;

    public ElasticsearchIndexAdapter(ElasticsearchClient client, IndicatorSearchIndex index) {
        this.client = client;
        this.index = index;
    }

    @Override
    public void indexAll(List<SearchIndexDocument> documents) {
        if (documents.isEmpty()) {
            return;
        }
        index.ensureExists();
        BulkRequest request = BulkRequest.of(b -> {
            for (SearchIndexDocument document : documents) {
                b.operations(op -> op.index(i -> i.index(IndicatorSearchIndex.NAME)
                        .id(document.documentId())
                        .document(IndicatorSearchIndex.toSource(document))));
            }
            return b;
        });
        report(execute(request, "索引寫入"));
    }

    @Override
    public void deleteAll(List<String> documentIds) {
        if (documentIds.isEmpty()) {
            return;
        }
        index.ensureExists();
        BulkRequest request = BulkRequest.of(b -> {
            for (String documentId : documentIds) {
                b.operations(
                        op -> op.delete(d -> d.index(IndicatorSearchIndex.NAME).id(documentId)));
            }
            return b;
        });
        report(execute(request, "索引刪除"));
    }

    @Override
    public long count() {
        if (!index.ensureExists()) {
            return 0L;
        }
        try {
            return client.count(c -> c.index(IndicatorSearchIndex.NAME)).count();
        } catch (IOException | RuntimeException e) {
            throw new ElasticsearchQueryException("Elasticsearch 計數失敗", e);
        }
    }

    @Override
    public List<IndexedDocument> documentsAfter(String afterId, int limit) {
        if (!index.ensureExists()) {
            return List.of();
        }
        SearchRequest request = SearchRequest.of(s -> {
            s.index(IndicatorSearchIndex.NAME)
                    .query(q -> q.matchAll(m -> m))
                    .source(source -> source.filter(f -> f.includes(SearchFields.UPDATED_AT_NANOS)))
                    .sort(sort -> sort.field(f -> f.field(SearchFields.ID).order(SortOrder.Asc)))
                    .size(limit);
            if (afterId != null) {
                s.searchAfter(FieldValue.of(afterId));
            }
            return s;
        });
        try {
            SearchResponse<Map> response = client.search(request, Map.class);
            return response.hits().hits().stream()
                    .map(hit -> new IndexedDocument(hit.id(), updatedAt(hit.source())))
                    .toList();
        } catch (IOException | RuntimeException e) {
            throw new ElasticsearchQueryException("Elasticsearch 掃描失敗", e);
        }
    }

    private static java.time.Instant updatedAt(Map<?, ?> source) {
        Object value = source == null ? null : source.get(SearchFields.UPDATED_AT_NANOS);
        // 沒有版本欄位的文件視為版本 0:reconciliation 會判定落後並重寫,這正是要的行為
        return EpochNanos.toInstant(value instanceof Number number ? number.longValue() : 0L);
    }

    private BulkResponse execute(BulkRequest request, String what) {
        try {
            return client.bulk(request);
        } catch (IOException | RuntimeException e) {
            throw new ElasticsearchQueryException("Elasticsearch " + what + "失敗", e);
        }
    }

    /** 部分失敗不丟例外:整批因為一筆而重做只會放大問題,留給 reconciliation 補。 */
    private static void report(BulkResponse response) {
        if (!response.errors()) {
            return;
        }
        for (BulkResponseItem item : response.items()) {
            if (item.error() != null) {
                log.warn("搜尋索引項目失敗 id={}:{}", item.id(), item.error().reason());
            }
        }
    }
}
