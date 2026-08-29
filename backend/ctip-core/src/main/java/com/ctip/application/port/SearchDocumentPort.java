package com.ctip.application.port;

import com.ctip.domain.indicator.IndicatorId;
import java.util.List;

/**
 * 從 <strong>source of truth</strong>(PostgreSQL)取出搜尋文件
 * (docs/spec/13-platform-ops.md §13.7:「Elasticsearch 僅為讀取索引,可隨時從 DB 重建」)。
 *
 * <p>刻意與 {@link IndicatorRepository} 分開:文件需要的是 {@code updated_at}、
 * 軟刪除狀態與來源的再散布政策,那是持久化層一次查詢就能給的投影,
 * 不該為此把聚合的重建路徑撐大。
 *
 * <p>三個方法一律<strong>排除軟刪除</strong>的 indicator——它們不該存在於索引中。
 */
public interface SearchDocumentPort {

    List<SearchIndexDocument> byIds(List<IndicatorId> ids);

    /** 依 indicator id 昇冪掃描,供 reconciliation 逐批比對;{@code afterId} 為 null 表示自頭開始。 */
    List<SearchIndexDocument> after(String afterId, int limit);

    long count();
}
