package com.ctip.testing;

import com.ctip.application.port.SearchDocumentPort;
import com.ctip.application.port.SearchIndexDocument;
import com.ctip.domain.indicator.IndicatorId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** 測試用的 source of truth 文件來源(以 indicator id 昇冪,語意與 PostgreSQL adapter 一致)。 */
public final class InMemorySearchDocuments implements SearchDocumentPort {

    private final Map<String, SearchIndexDocument> documents = new TreeMap<>();

    public void put(SearchIndexDocument document) {
        documents.put(document.documentId(), document);
    }

    public void remove(String documentId) {
        documents.remove(documentId);
    }

    @Override
    public List<SearchIndexDocument> byIds(List<IndicatorId> ids) {
        return ids.stream()
                .map(id -> documents.get(id.value().toString()))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Override
    public List<SearchIndexDocument> after(String afterId, int limit) {
        return documents.entrySet().stream()
                .filter(entry -> afterId == null || entry.getKey().compareTo(afterId) > 0)
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .limit(limit)
                .map(Map.Entry::getValue)
                .toList();
    }

    @Override
    public long count() {
        return documents.size();
    }
}
