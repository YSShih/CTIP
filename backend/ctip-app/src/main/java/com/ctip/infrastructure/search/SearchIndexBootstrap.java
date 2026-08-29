package com.ctip.infrastructure.search;

import com.ctip.application.port.SearchDocumentPort;
import com.ctip.application.port.SearchIndexPort;
import com.ctip.application.search.ReconciliationReport;
import com.ctip.application.search.SearchReconciliationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

/**
 * 索引是空的、資料庫不是空的時候,啟動後補建一次
 * (docs/spec/13-platform-ops.md §13.7:「Elasticsearch 僅為讀取索引,<strong>可隨時從 DB 重建</strong>」)。
 *
 * <p>沒有這一步的話,全新的 ES 叢集(或索引被刪掉之後)會在 05:00 的對帳之前一直是空的,
 * 而搜尋<strong>照樣回 200 並宣稱 {@code X-Search-Backend: elasticsearch}</strong>——
 * 那比降級更糟:降級至少會說出來,空索引是靜默的錯誤答案。
 *
 * <p>只在「索引空、資料庫非空」時執行,因此正常重啟不會付出任何代價;
 * 在背景執行緒跑,不阻塞啟動與 readiness;任何例外只記錄,05:00 的對帳仍會再試一次。
 */
public class SearchIndexBootstrap {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexBootstrap.class);

    private final SearchIndexPort index;
    private final SearchDocumentPort documents;
    private final SearchReconciliationService reconciliation;

    public SearchIndexBootstrap(
            SearchIndexPort index, SearchDocumentPort documents, SearchReconciliationService reconciliation) {
        this.index = index;
        this.documents = documents;
        this.reconciliation = reconciliation;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void buildIndexIfEmpty() {
        Thread worker = new Thread(this::rebuildIfEmpty, "ctip-search-index-bootstrap");
        worker.setDaemon(true);
        worker.start();
    }

    /** package-private 供單元測試同步呼叫;正式路徑一律走上面的背景執行緒。 */
    void rebuildIfEmpty() {
        try {
            if (index.count() > 0 || documents.count() == 0) {
                return;
            }
            log.info("搜尋索引是空的而資料庫不是,啟動後補建一次(§13.7:索引可隨時從 DB 重建)");
            ReconciliationReport report = reconciliation.reconcile();
            log.info("搜尋索引補建完成:{} 筆", report.reindexedMissing());
        } catch (RuntimeException e) {
            log.warn("搜尋索引補建失敗,將由每日對帳重試", e);
        }
    }
}
