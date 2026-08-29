package com.ctip.support;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.ctip.application.search.ReconciliationReport;
import com.ctip.application.search.SearchReconciliationService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

/**
 * heavy 測試對搜尋索引的直接操作:全量重建、強制 refresh、以及「植入一筆索引不該有的文件」。
 *
 * <p>refresh 必須顯式呼叫:正式路徑刻意不強制 refresh(索引是最終一致的讀取副本),
 * 但測試若不等它可見,量到的會是時序而不是行為。
 */
public final class SearchIndexControl {

    public static final String INDEX = "ctip-indicators";

    private final ElasticsearchClient client;
    private final SearchReconciliationService reconciliation;

    public SearchIndexControl(ElasticsearchClient client, SearchReconciliationService reconciliation) {
        this.client = client;
        this.reconciliation = reconciliation;
    }

    /** 以 source of truth 重建整個索引(§13.7:「可隨時從 DB 重建」),並等它可見。 */
    public ReconciliationReport rebuild() {
        ReconciliationReport report = reconciliation.reconcile();
        refresh();
        return report;
    }

    public ReconciliationReport reconcile() {
        ReconciliationReport report = reconciliation.reconcile();
        refresh();
        return report;
    }

    public void refresh() {
        try {
            client.indices().refresh(r -> r.index(INDEX));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 直接寫入一筆文件,繞過所有應用層邏輯——用來驗證「索引被污染時仍不得洩漏」。 */
    public void poison(String documentId, Map<String, Object> source) {
        try {
            client.index(i -> i.index(INDEX).id(documentId).document(source));
            refresh();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void delete(String documentId) {
        try {
            client.delete(d -> d.index(INDEX).id(documentId));
            refresh();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public long count() {
        try {
            return client.count(c -> c.index(INDEX)).count();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
