package com.ctip.application.search;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.ctip.application.port.IndexedDocument;
import com.ctip.application.port.SearchDocumentPort;
import com.ctip.application.port.SearchIndexDocument;
import com.ctip.application.port.SearchIndexPort;
import com.ctip.domain.indicator.IndicatorId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * §13.7:「索引失敗不得使 ingestion 失敗,只記錄並排入重試」。
 * 這是 {@code StixProjectionWriter} 的同型契約——外部系統的故障不得沿著攝取路徑往上冒。
 */
@Tag("unit")
class SearchIndexWriterTest {

    @Test
    void indexFailuresAreSwallowedSoIngestionSurvives() {
        SearchDocumentPort documents = new SearchDocumentPort() {
            @Override
            public List<SearchIndexDocument> byIds(List<IndicatorId> ids) {
                throw new IllegalStateException("資料庫投影失敗");
            }

            @Override
            public List<SearchIndexDocument> after(String afterId, int limit) {
                return List.of();
            }

            @Override
            public long count() {
                return 0;
            }
        };
        SearchIndexWriter writer = new SearchIndexWriter(documents, new ExplodingIndex());

        assertThatCode(() -> writer.indexAll(List.of(new IndicatorId(UUID.randomUUID()))))
                .doesNotThrowAnyException();
    }

    @Test
    void anEmptyBatchDoesNotTouchTheIndex() {
        SearchIndexWriter writer = new SearchIndexWriter(new ExplodingDocuments(), new ExplodingIndex());

        assertThatCode(() -> writer.indexAll(List.of())).doesNotThrowAnyException();
    }

    private static final class ExplodingIndex implements SearchIndexPort {
        @Override
        public void indexAll(List<SearchIndexDocument> documents) {
            throw new IllegalStateException("索引不可用");
        }

        @Override
        public void deleteAll(List<String> documentIds) {
            throw new IllegalStateException("索引不可用");
        }

        @Override
        public long count() {
            throw new IllegalStateException("索引不可用");
        }

        @Override
        public List<IndexedDocument> documentsAfter(String afterId, int limit) {
            throw new IllegalStateException("索引不可用");
        }
    }

    private static final class ExplodingDocuments implements SearchDocumentPort {
        @Override
        public List<SearchIndexDocument> byIds(List<IndicatorId> ids) {
            throw new IllegalStateException("不該被呼叫");
        }

        @Override
        public List<SearchIndexDocument> after(String afterId, int limit) {
            throw new IllegalStateException("不該被呼叫");
        }

        @Override
        public long count() {
            throw new IllegalStateException("不該被呼叫");
        }
    }
}
