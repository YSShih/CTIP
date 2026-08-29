package com.ctip;

import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.ctip.application.port.IndicatorRepository;
import com.ctip.application.port.SourceRepository;
import com.ctip.application.search.ReconciliationReport;
import com.ctip.application.search.SearchReconciliationService;
import com.ctip.domain.indicator.IndicatorId;
import com.ctip.domain.source.SourceId;
import com.ctip.domain.tenant.TenantId;
import com.ctip.sdk.RedistributionPolicy;
import com.ctip.sdk.SourceType;
import com.ctip.sdk.Tlp;
import com.ctip.support.BloomFixtures;
import com.ctip.support.ElasticsearchTestContainer;
import com.ctip.support.IndicatorFixtures;
import com.ctip.support.SearchIndexControl;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * DoD M2-24:reconciliation 能偵測並修正 DB 與 ES 的差異
 * (docs/spec/13-platform-ops.md §13.7「比對 DB 與 ES 的筆數與版本」,每日 05:00;08 §8.7)。
 *
 * <p>三種漂移各一個案例:索引缺這筆、索引版本落後、索引有而 DB 沒有(孤兒)。
 * 修正方向永遠是<strong>以 DB 為準</strong>——PostgreSQL 是 source of truth,
 * 對帳絕不能反過來把資料庫改成索引的樣子。
 */
@Tag("heavy")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(properties = "ctip.search.backend=elasticsearch")
class SearchReconciliationTest extends AbstractPostgresIntegrationTest {

    private static final String NAME = "recon-target";
    private static final String ORPHAN_ID = "00000000-0000-0000-0000-0000000fa11e";

    @Autowired
    private ElasticsearchClient client;

    @Autowired
    private SearchReconciliationService reconciliation;

    @Autowired
    private IndicatorRepository indicators;

    @Autowired
    private SourceRepository sources;

    private SearchIndexControl index;
    private IndicatorId target;

    @DynamicPropertySource
    static void elasticsearch(DynamicPropertyRegistry registry) {
        registry.add("ELASTICSEARCH_URL", ElasticsearchTestContainer::url);
    }

    @BeforeAll
    void seed() {
        SourceId sourceId = sources.findBySourceType(SourceType.MOCK_OPENPHISH)
                .orElseThrow()
                .id();
        target = BloomFixtures.id("00000ec1");
        IndicatorFixtures.upsert(
                indicators,
                sourceId,
                new IndicatorFixtures.Fixture(
                        target, TenantId.PUBLIC, Tlp.CLEAR, RedistributionPolicy.PUBLIC_REDISTRIBUTABLE, NAME));
        index = new SearchIndexControl(client, reconciliation);
    }

    @BeforeEach
    void startInSync() {
        index.rebuild();
    }

    @Test
    void aFullyReconciledIndexReportsNoDrift() {
        ReconciliationReport report = index.reconcile();
        assertThat(report.inSync()).isTrue();
        assertThat(report.databaseCount()).isEqualTo(report.indexCountBefore()).isPositive();
    }

    @Test
    void aMissingDocumentIsDetectedAndReindexed() {
        index.delete(target.value().toString());
        long before = index.count();

        ReconciliationReport report = index.reconcile();

        assertThat(report.reindexedMissing()).isEqualTo(1);
        assertThat(report.reindexedStale()).isZero();
        assertThat(report.deletedOrphans()).isZero();
        assertThat(index.count()).isEqualTo(before + 1);
        assertThat(index.reconcile().inSync()).isTrue();
    }

    @Test
    void aStaleDocumentVersionIsDetectedAndRewritten() {
        // 版本欄位倒退 = 索引落後於 updated_at,內容也一併寫錯,對帳必須把它蓋回去
        index.poison(target.value().toString(), staleSource(target.value().toString()));

        ReconciliationReport report = index.reconcile();

        assertThat(report.reindexedStale()).isEqualTo(1);
        assertThat(report.reindexedMissing()).isZero();
        assertThat(index.reconcile().inSync()).isTrue();
    }

    @Test
    void anOrphanDocumentIsDetectedAndDeleted() {
        index.poison(ORPHAN_ID, staleSource(ORPHAN_ID));
        long before = index.count();

        ReconciliationReport report = index.reconcile();

        assertThat(report.deletedOrphans()).isEqualTo(1);
        assertThat(index.count()).isEqualTo(before - 1);
        assertThat(index.reconcile().inSync()).isTrue();
    }

    private Map<String, Object> staleSource(String id) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("id", id);
        source.put("ownerTenantId", TenantId.PUBLIC.value().toString());
        source.put("value", NAME + ".security.ctip-sample.net");
        source.put("normalizedValue", NAME + ".security.ctip-sample.net");
        source.put("type", "DOMAIN");
        source.put("severity", "MEDIUM");
        source.put("status", "ACTIVE");
        source.put("tlp", "CLEAR");
        source.put("tags", List.of());
        source.put("confidence", 1);
        source.put("score", 1);
        source.put("firstSeen", IndicatorFixtures.SEEN.toString());
        source.put("lastSeen", IndicatorFixtures.SEEN.toString());
        source.put("validUntil", null);
        source.put("lastSeenNanos", IndicatorFixtures.SEEN.getEpochSecond() * 1_000_000_000L);
        source.put("redistributable", true);
        source.put("sourceIds", List.of());
        source.put("disclosableSourceIds", List.of());
        source.put("updatedAtNanos", Instant.EPOCH.getEpochSecond() * 1_000_000_000L);
        return source;
    }
}
