package com.ctip.testing;

import com.ctip.application.port.IndexedDocument;
import com.ctip.application.port.SearchIndexDocument;
import com.ctip.application.port.SearchIndexPort;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** 測試用的記憶體搜尋索引(以文件 id 排序,語意與 ES 的 id 昇冪掃描一致)。 */
public final class InMemorySearchIndex implements SearchIndexPort {

    private final Map<String, Instant> versions = new TreeMap<>();
    private final Map<String, SearchIndexDocument> documents = new LinkedHashMap<>();

    @Override
    public void indexAll(List<SearchIndexDocument> batch) {
        for (SearchIndexDocument document : batch) {
            versions.put(document.documentId(), document.updatedAt());
            documents.put(document.documentId(), document);
        }
    }

    @Override
    public void deleteAll(List<String> documentIds) {
        documentIds.forEach(versions::remove);
        documentIds.forEach(documents::remove);
    }

    @Override
    public long count() {
        return versions.size();
    }

    @Override
    public List<IndexedDocument> documentsAfter(String afterId, int limit) {
        return versions.entrySet().stream()
                .filter(entry -> afterId == null || entry.getKey().compareTo(afterId) > 0)
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .limit(limit)
                .map(entry -> new IndexedDocument(entry.getKey(), entry.getValue()))
                .toList();
    }

    /** 測試專用:直接植入一筆版本(模擬索引落後或孤兒文件)。 */
    public void putRaw(String documentId, Instant version) {
        versions.put(documentId, version);
    }

    public boolean contains(String documentId) {
        return versions.containsKey(documentId);
    }

    public SearchIndexDocument document(String documentId) {
        return documents.get(documentId);
    }
}
