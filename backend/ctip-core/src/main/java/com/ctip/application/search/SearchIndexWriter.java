package com.ctip.application.search;

import com.ctip.application.port.SearchDocumentPort;
import com.ctip.application.port.SearchIndexDocument;
import com.ctip.application.port.SearchIndexPort;
import com.ctip.domain.indicator.IndicatorId;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 攝取批次提交<strong>之後</strong>把受影響的 indicator 寫進搜尋索引
 * (docs/spec/13-platform-ops.md §13.7、08 §8.2 stage 11)。
 *
 * <p>§13.7:「索引失敗不得使 ingestion 失敗,只記錄並排入重試」。這裡的「重試」由每日 05:00 的
 * reconciliation 承擔({@link SearchReconciliationService})——它本來就會把 ES 缺漏或版本落後的
 * 文件補回來,再另建一個記憶體重試佇列只會多一個會在重啟時遺失的真相來源。
 *
 * <p>文件一律從 source of truth 重新讀取(而非沿用交易內的聚合狀態):
 * {@code updated_at} 等欄位在提交後才確定,而 reconciliation 正是拿它來比對版本。
 */
@Service
public class SearchIndexWriter {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexWriter.class);

    private final SearchDocumentPort documents;
    private final SearchIndexPort index;

    public SearchIndexWriter(SearchDocumentPort documents, SearchIndexPort index) {
        this.documents = documents;
        this.index = index;
    }

    public void indexAll(List<IndicatorId> ids) {
        if (ids.isEmpty()) {
            return;
        }
        try {
            List<SearchIndexDocument> batch = documents.byIds(ids);
            index.indexAll(batch);
        } catch (RuntimeException e) {
            log.warn("搜尋索引寫出失敗,只記錄不影響 ingestion(§13.7);{} 筆待 reconciliation 修正", ids.size(), e);
        }
    }
}
