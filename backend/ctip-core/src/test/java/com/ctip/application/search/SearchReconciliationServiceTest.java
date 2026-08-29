package com.ctip.application.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctip.application.port.SearchIndexDocument;
import com.ctip.domain.indicator.IndicatorId;
import com.ctip.testing.InMemorySearchDocuments;
import com.ctip.testing.InMemorySearchIndex;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * 對帳的歸併比對(docs/spec/13-platform-ops.md §13.7)。修正方向永遠是以 DB 為準改索引。
 * 真實 Elasticsearch 的版本由 {@code SearchReconciliationTest}(L4)驗證,這裡量的是演算法本身:
 * 缺漏、版本落後、孤兒三種漂移,以及「多批」時不會把還沒掃到的文件誤判成孤兒。
 */
@Tag("unit")
class SearchReconciliationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-29T00:00:00Z");

    private final InMemorySearchDocuments database = new InMemorySearchDocuments();
    private final InMemorySearchIndex index = new InMemorySearchIndex();
    private final SearchReconciliationService service = new SearchReconciliationService(database, index);

    @Test
    void anIndexThatMatchesTheDatabaseReportsNoDrift() {
        SearchIndexDocument document = document(1, NOW);
        database.put(document);
        index.indexAll(java.util.List.of(document));

        ReconciliationReport report = service.reconcile();

        assertThat(report.inSync()).isTrue();
        assertThat(report.databaseCount()).isEqualTo(1);
        assertThat(report.indexCountBefore()).isEqualTo(1);
    }

    @Test
    void missingDocumentsAreReindexed() {
        database.put(document(1, NOW));

        ReconciliationReport report = service.reconcile();

        assertThat(report.reindexedMissing()).isEqualTo(1);
        assertThat(index.contains(id(1).toString())).isTrue();
        assertThat(service.reconcile().inSync()).isTrue();
    }

    @Test
    void staleVersionsAreRewritten() {
        SearchIndexDocument current = document(1, NOW);
        database.put(current);
        index.putRaw(current.documentId(), NOW.minusSeconds(3600));

        ReconciliationReport report = service.reconcile();

        assertThat(report.reindexedStale()).isEqualTo(1);
        assertThat(report.reindexedMissing()).isZero();
        assertThat(index.document(current.documentId()).updatedAt()).isEqualTo(NOW);
    }

    @Test
    void orphansAreDeleted() {
        index.putRaw(id(9).toString(), NOW);

        ReconciliationReport report = service.reconcile();

        assertThat(report.deletedOrphans()).isEqualTo(1);
        assertThat(index.contains(id(9).toString())).isFalse();
        assertThat(index.count()).isZero();
    }

    /**
     * 多批時的關鍵:一批只在「兩邊共同涵蓋的 id 區間」內下判斷。
     * 若不設邊界,批次尾端之後、下一批之前的文件會在每一輪被誤判成孤兒並刪掉,
     * 對帳會把索引愈修愈空。
     */
    @Test
    void multipleBatchesConvergeWithoutDeletingUnscannedDocuments() {
        for (int i = 1; i <= 1200; i++) {
            SearchIndexDocument document = document(i, NOW);
            database.put(document);
            index.indexAll(java.util.List.of(document));
        }
        index.deleteAll(java.util.List.of(id(700).toString()));

        ReconciliationReport report = service.reconcile();

        assertThat(report.reindexedMissing()).isEqualTo(1);
        assertThat(report.deletedOrphans()).isZero();
        assertThat(index.count()).isEqualTo(1200);
        assertThat(service.reconcile().inSync()).isTrue();
    }

    private static UUID id(int n) {
        return new UUID(0L, n);
    }

    private static SearchIndexDocument document(int n, Instant updatedAt) {
        return new SearchIndexDocument(
                new IndicatorId(id(n)),
                new UUID(0L, 0L).toString(),
                "value-" + n,
                "value-" + n,
                "DOMAIN",
                "MEDIUM",
                "ACTIVE",
                "CLEAR",
                60,
                50,
                Set.of("t"),
                NOW,
                NOW,
                null,
                true,
                Set.of(),
                Set.of(),
                updatedAt);
    }
}
