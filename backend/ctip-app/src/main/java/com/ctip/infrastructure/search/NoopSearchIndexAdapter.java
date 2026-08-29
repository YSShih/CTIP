package com.ctip.infrastructure.search;

import com.ctip.application.port.IndexedDocument;
import com.ctip.application.port.SearchIndexDocument;
import com.ctip.application.port.SearchIndexPort;
import java.util.List;

/**
 * {@code SEARCH_BACKEND=postgres} 時的索引寫入面:什麼都不做。
 *
 * <p>這不是 placeholder(執行規則 16):在 PostgreSQL 後端下「不維護外部索引」就是正確且完整的行為,
 * mvp/dev 的 compose 根本不啟動 Elasticsearch。對帳排程在此模式下不註冊({@code SearchSchedulers}
 * 只在 ES 後端裝配),因此不會每天產生一份「整個索引都缺」的假警報。
 */
public class NoopSearchIndexAdapter implements SearchIndexPort {

    @Override
    public void indexAll(List<SearchIndexDocument> documents) {
        // 無外部索引可寫
    }

    @Override
    public void deleteAll(List<String> documentIds) {
        // 無外部索引可刪
    }

    @Override
    public long count() {
        return 0L;
    }

    @Override
    public List<IndexedDocument> documentsAfter(String afterId, int limit) {
        return List.of();
    }
}
