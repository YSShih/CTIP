package com.ctip.application.port;

import java.util.List;

/**
 * 搜尋索引的寫入面(docs/spec/13-platform-ops.md §13.7)。讀取面是 {@link SearchPort}。
 *
 * <p>簽章只用 domain 與 JDK 型別:06 §6.5 要求 Elasticsearch → OpenSearch 的替換只需改
 * infrastructure 實作,ArchUnit 規則 11 會強制這件事。
 *
 * <p>索引後端未啟用時由一個什麼都不做的實作承接——「不索引」是真實行為,不是 placeholder。
 */
public interface SearchIndexPort {

    /** 冪等寫入(以文件 id 覆寫)。實作不得讓失敗傳播成 ingestion 失敗,由呼叫端隔離。 */
    void indexAll(List<SearchIndexDocument> documents);

    void deleteAll(List<String> documentIds);

    /** 索引中的文件總數。 */
    long count();

    /** 依文件 id 昇冪掃描;{@code afterId} 為 null 表示自頭開始。 */
    List<IndexedDocument> documentsAfter(String afterId, int limit);
}
