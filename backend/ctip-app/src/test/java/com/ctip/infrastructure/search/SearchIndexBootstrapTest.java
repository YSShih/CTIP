package com.ctip.infrastructure.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.ctip.application.port.IndexedDocument;
import com.ctip.application.port.SearchDocumentPort;
import com.ctip.application.port.SearchIndexDocument;
import com.ctip.application.port.SearchIndexPort;
import com.ctip.application.search.SearchReconciliationService;
import com.ctip.domain.indicator.IndicatorId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * 全新的 ES 叢集不得等到 05:00 才有資料(docs/spec/13-platform-ops.md §13.7)。
 *
 * <p>空索引比降級更危險:搜尋照樣回 200 並宣稱 {@code X-Search-Backend: elasticsearch},
 * 但答案是錯的而且沒有任何徵兆。同樣重要的是<strong>正常重啟不得重建</strong>——
 * 否則每次部署都會對整個資料集跑一次全量寫入。
 */
@Tag("unit")
class SearchIndexBootstrapTest {

    private static final Instant NOW = Instant.parse("2026-08-29T00:00:00Z");

    @Test
    void anEmptyIndexOverANonEmptyDatabaseIsRebuilt() {
        RecordingIndex index = new RecordingIndex();
        StubDocuments documents = new StubDocuments(List.of(document(1)));

        bootstrap(index, documents).rebuildIfEmpty();

        assertThat(index.indexed).hasSize(1);
    }

    @Test
    void anIndexThatAlreadyHasDocumentsIsLeftAlone() {
        RecordingIndex index = new RecordingIndex();
        index.existing = 5;

        bootstrap(index, new StubDocuments(List.of(document(1)))).rebuildIfEmpty();

        assertThat(index.indexed).isEmpty();
    }

    @Test
    void anEmptyDatabaseIsNotMistakenForDrift() {
        RecordingIndex index = new RecordingIndex();

        bootstrap(index, new StubDocuments(List.of())).rebuildIfEmpty();

        assertThat(index.indexed).isEmpty();
    }

    /** ES 還沒起來不得使啟動失敗:例外只記錄,05:00 的對帳會再試。 */
    @Test
    void indexFailuresDoNotEscape() {
        SearchIndexPort broken = new RecordingIndex() {
            @Override
            public long count() {
                throw new IllegalStateException("ES 不可用");
            }
        };

        assertThatCode(() -> bootstrap(broken, new StubDocuments(List.of(document(1))))
                        .rebuildIfEmpty())
                .doesNotThrowAnyException();
    }

    private static SearchIndexBootstrap bootstrap(SearchIndexPort index, SearchDocumentPort documents) {
        return new SearchIndexBootstrap(index, documents, new SearchReconciliationService(documents, index));
    }

    private static SearchIndexDocument document(int n) {
        return new SearchIndexDocument(
                new IndicatorId(new UUID(0L, n)),
                new UUID(0L, 0L).toString(),
                "v" + n,
                "v" + n,
                "DOMAIN",
                "MEDIUM",
                "ACTIVE",
                "CLEAR",
                60,
                50,
                Set.of(),
                NOW,
                NOW,
                null,
                true,
                Set.of(),
                Set.of(),
                NOW);
    }

    private static class RecordingIndex implements SearchIndexPort {

        private final List<SearchIndexDocument> indexed = new ArrayList<>();
        private long existing;

        @Override
        public void indexAll(List<SearchIndexDocument> documents) {
            indexed.addAll(documents);
        }

        @Override
        public void deleteAll(List<String> documentIds) {
            // 本測試的情境不會刪任何東西
        }

        @Override
        public long count() {
            return existing;
        }

        @Override
        public List<IndexedDocument> documentsAfter(String afterId, int limit) {
            return List.of();
        }
    }

    private record StubDocuments(List<SearchIndexDocument> all) implements SearchDocumentPort {

        @Override
        public List<SearchIndexDocument> byIds(List<IndicatorId> ids) {
            return all;
        }

        @Override
        public List<SearchIndexDocument> after(String afterId, int limit) {
            return afterId == null ? all : List.of();
        }

        @Override
        public long count() {
            return all.size();
        }
    }
}
