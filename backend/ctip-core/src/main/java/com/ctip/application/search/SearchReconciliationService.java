package com.ctip.application.search;

import com.ctip.application.port.IndexedDocument;
import com.ctip.application.port.SearchDocumentPort;
import com.ctip.application.port.SearchIndexDocument;
import com.ctip.application.port.SearchIndexPort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * DB 與搜尋索引的對帳(docs/spec/13-platform-ops.md §13.7:「提供 reconciliation 排程比對 DB 與 ES 的
 * 筆數與版本並修正」,每日 05:00,環境變數 {@code ES_RECONCILE_CRON};08 §8.7)。
 *
 * <p>PostgreSQL 永遠是 source of truth,因此修正方向<strong>只有一個</strong>:以 DB 為準改索引。
 * 三種漂移:索引缺這筆(補)、索引版本落後 {@code updated_at}(重寫)、索引有而 DB 沒有
 * (刪除——含軟刪除後殘留的孤兒文件)。
 *
 * <p>兩邊都以文件 id 昇冪掃描後做歸併比對,記憶體用量與資料量無關(只保留一批)。
 * 它同時是 {@link SearchIndexWriter} 的「排入重試」機制:寫出失敗的文件在此被補回來。
 */
@Service
public class SearchReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(SearchReconciliationService.class);

    /** 一次比對的批次大小;兩邊用同一個值,歸併才不會退化成全表載入。 */
    private static final int BATCH = 500;

    private final SearchDocumentPort documents;
    private final SearchIndexPort index;

    public SearchReconciliationService(SearchDocumentPort documents, SearchIndexPort index) {
        this.documents = documents;
        this.index = index;
    }

    public ReconciliationReport reconcile() {
        long databaseCount = documents.count();
        long indexCount = index.count();
        Drift drift = new Drift();
        String afterId = null;
        while (true) {
            List<SearchIndexDocument> expected = documents.after(afterId, BATCH);
            List<IndexedDocument> actual = index.documentsAfter(afterId, BATCH);
            if (expected.isEmpty() && actual.isEmpty()) {
                break;
            }
            // 兩邊各取一批後,只在「兩批共同涵蓋的 id 區間」內下判斷:超出區間的 id
            // 可能只是還沒掃到,當成漂移會把正常資料反覆重寫、把尚未比對到的文件誤刪。
            String boundary = boundary(expected, actual);
            apply(drift, expected, actual, boundary);
            afterId = boundary;
        }
        flush(drift);
        ReconciliationReport report =
                new ReconciliationReport(databaseCount, indexCount, drift.missing, drift.stale, drift.orphans);
        if (!report.inSync()) {
            log.warn(
                    "搜尋索引與資料庫不一致並已修正:DB {} 筆 / 索引 {} 筆,補 {}、重寫 {}、刪 {}",
                    databaseCount,
                    indexCount,
                    drift.missing,
                    drift.stale,
                    drift.orphans);
        }
        return report;
    }

    /** 共同涵蓋的上界:任一邊已掃完就以另一邊為準,兩邊都滿批則取較小的末端 id。 */
    private static String boundary(List<SearchIndexDocument> expected, List<IndexedDocument> actual) {
        String expectedEnd = expected.isEmpty() ? null : expected.getLast().documentId();
        String actualEnd = actual.isEmpty() ? null : actual.getLast().documentId();
        if (expectedEnd == null) {
            return actualEnd;
        }
        if (actualEnd == null) {
            return expectedEnd;
        }
        return expectedEnd.compareTo(actualEnd) <= 0 ? expectedEnd : actualEnd;
    }

    private void apply(Drift drift, List<SearchIndexDocument> expected, List<IndexedDocument> actual, String boundary) {
        Map<String, Instant> indexed = new LinkedHashMap<>();
        for (IndexedDocument document : actual) {
            if (document.documentId().compareTo(boundary) <= 0) {
                indexed.put(document.documentId(), document.updatedAt());
            }
        }
        for (SearchIndexDocument document : expected) {
            if (document.documentId().compareTo(boundary) > 0) {
                continue;
            }
            Instant version = indexed.remove(document.documentId());
            if (version == null) {
                drift.reindex.add(document);
                drift.missing++;
            } else if (!version.equals(document.updatedAt())) {
                drift.reindex.add(document);
                drift.stale++;
            }
        }
        // 掃完 expected 後仍留在 map 中的,就是 DB 沒有(或已軟刪除)的孤兒文件
        drift.delete.addAll(indexed.keySet());
        drift.orphans += indexed.size();
        drift.flushIfFull(index);
    }

    private void flush(Drift drift) {
        drift.flush(index);
    }

    /** 累積待修正項並分批送出,避免一次 bulk 撐爆請求大小。 */
    private static final class Drift {
        private final List<SearchIndexDocument> reindex = new ArrayList<>();
        private final List<String> delete = new ArrayList<>();
        private int missing;
        private int stale;
        private int orphans;

        void flushIfFull(SearchIndexPort index) {
            if (reindex.size() >= BATCH || delete.size() >= BATCH) {
                flush(index);
            }
        }

        void flush(SearchIndexPort index) {
            if (!reindex.isEmpty()) {
                index.indexAll(List.copyOf(reindex));
                reindex.clear();
            }
            if (!delete.isEmpty()) {
                index.deleteAll(List.copyOf(delete));
                delete.clear();
            }
        }
    }
}
