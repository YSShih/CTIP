package com.ctip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ctip.application.source.SourceSyncOutcome;
import com.ctip.application.source.SourceSyncService;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Ingestion 端到端(docs/spec/08-ingestion-sdk.md §8.2、03 §3.4):三個 mock 全啟用,
 * fetch → pipeline → PostgreSQL;驗證正規化落庫、拒絕記錄、source_sync、跨來源合併與
 * 再同步的 UPSERT 冪等。測試自行清理(啟用旗標與新增資料),不影響種子斷言。
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "ctip.data-quality.domain-allowlist=allowlisted.example.com")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IngestionEndToEndTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private SourceSyncService sourceSyncService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @BeforeAll
    void enableAllMocksAndSnapshotIndicators() {
        jdbc.execute("CREATE TABLE e2e_indicator_snapshot AS SELECT id FROM indicators");
        jdbc.update("UPDATE sources SET enabled = true WHERE source_type IN ('MOCK_ABUSEIPDB','MOCK_ALIENVAULT')");
    }

    @AfterAll
    void restoreSeedState() {
        jdbc.update("DELETE FROM ingestion_rejections");
        jdbc.update("DELETE FROM source_sync");
        jdbc.update("DELETE FROM indicators WHERE id NOT IN (SELECT id FROM e2e_indicator_snapshot)");
        jdbc.execute("DROP TABLE e2e_indicator_snapshot");
        jdbc.update("UPDATE sources SET enabled = (source_type IN ('MANUAL','MOCK_OPENPHISH')), status = 'ACTIVE',"
                + " consecutive_failures = 0, last_sync_at = NULL, last_success_at = NULL, last_failure_at = NULL,"
                + " last_error_message = NULL, avg_latency_ms = NULL, next_cursor = NULL, total_records_ingested = 0");
    }

    @Test
    @Order(1)
    void firstSyncIngestsAllThreeMocksWithRejectionsRecorded() {
        List<SourceSyncOutcome> outcomes = sourceSyncService.syncDueSources();

        assertThat(outcomes).hasSize(3).allMatch(SourceSyncOutcome::success);
        int fetched =
                outcomes.stream().mapToInt(SourceSyncOutcome::recordsFetched).sum();
        int accepted =
                outcomes.stream().mapToInt(SourceSyncOutcome::recordsAccepted).sum();
        int rejected =
                outcomes.stream().mapToInt(SourceSyncOutcome::recordsRejected).sum();
        int merged =
                outcomes.stream().mapToInt(SourceSyncOutcome::recordsMerged).sum();
        assertThat(fetched).isEqualTo(56); // 20 + 17 + 19(三個 mock 的固定資料集)
        assertThat(accepted).isEqualTo(44);
        assertThat(rejected).isEqualTo(12);
        assertThat(merged).isEqualTo(11); // SharedIocs 的 11 個跨來源重疊

        // 拒絕全部落 ingestion_rejections,不得靜默丟棄(§7.3);feed 可觸發的七種 reason 齊備
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ingestion_rejections", Integer.class))
                .isEqualTo(12);
        assertThat(jdbc.queryForList("SELECT DISTINCT reason FROM ingestion_rejections", String.class))
                .containsExactlyInAnyOrder(
                        "MALFORMED_VALUE",
                        "PRIVATE_OR_RESERVED_IP",
                        "ALLOWLISTED_DOMAIN",
                        "LENGTH_EXCEEDED",
                        "HASH_LENGTH_MISMATCH",
                        "UNKNOWN_TYPE",
                        "DUPLICATE_IN_BATCH");

        // source_sync:一次執行一列,計數自洽,有拒絕 → PARTIAL(表 3)
        List<String> results = jdbc.queryForList("SELECT result FROM source_sync", String.class);
        assertThat(results).hasSize(3).allMatch("PARTIAL"::equals);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM source_sync WHERE finished_at IS NULL"
                                + " OR records_fetched <> records_accepted + records_rejected",
                        Integer.class))
                .isZero();
    }

    @Test
    @Order(2)
    void normalizationIsMaterializedInDatabase() {
        assertThat(normalizedExists("https://upper-case.example.com/Path?a=1&b=2"))
                .isTrue(); // 大小寫/port/query/fragment
        assertThat(normalizedExists("zerowidth.example.net")).isTrue(); // 零寬字元移除
        assertThat(normalizedExists("trailing-dot.example.com")).isTrue(); // 尾端點 + 小寫
        assertThat(normalizedExists("203.0.113.7")).isTrue(); // IPv4 前導零
        assertThat(normalizedExists("2001:db8::1")).isTrue(); // IPv6 RFC 5952
        assertThat(normalizedExists("2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae"))
                .isTrue(); // 雜湊小寫
        assertThat(normalizedExists("Spear.Phisher@example.org")).isTrue(); // email local 保留大小寫
    }

    @Test
    @Order(3)
    void crossSourceMergeAggregatesPerSpec() {
        // shared-phish-1:OpenPhish(conf 90, rep 70)+ AlienVault(conf 25, rep 60)
        // → confidence = round((90×70 + 25×60) / 130) = 60;severity MAX(HIGH, LOW);TLP 皆 CLEAR
        var row = jdbc.queryForMap("SELECT confidence, severity, tlp, source_count, status, score, valid_until"
                + " FROM indicators WHERE normalized_value = 'shared-phish-1.example.com'");
        assertThat(((Number) row.get("confidence")).intValue()).isEqualTo(60);
        assertThat(row.get("severity")).isEqualTo("HIGH");
        assertThat(row.get("tlp")).isEqualTo("CLEAR");
        assertThat(((Number) row.get("source_count")).intValue()).isEqualTo(2);
        assertThat(row.get("status")).isEqualTo("ACTIVE");
        assertThat(((Number) row.get("score")).intValue()).isPositive();
        assertThat(row.get("valid_until")).isNotNull(); // 三步過期計算:來源未明示 → 型別預設 TTL

        // TLP 取最嚴格:AbuseIPDB(GREEN)+ AlienVault(CLEAR)→ GREEN
        assertThat(jdbc.queryForObject(
                        "SELECT tlp FROM indicators WHERE normalized_value = '198.51.100.23'", String.class))
                .isEqualTo("GREEN");

        // AlienVault 撤回(reputation 60 < 80):來源記錄 RETRACTED,indicator 不得 REVOKED(I11 規則 1)
        assertThat(jdbc.queryForObject(
                        "SELECT s.status FROM indicator_sources s"
                                + " JOIN indicators i ON i.id = s.indicator_id"
                                + " JOIN sources src ON src.id = s.source_id"
                                + " WHERE i.normalized_value = 'shared-phish-2.example.com'"
                                + " AND src.source_type = 'MOCK_ALIENVAULT'",
                        String.class))
                .isEqualTo("RETRACTED");
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM indicators WHERE normalized_value = 'shared-phish-2.example.com'",
                        String.class))
                .isEqualTo("ACTIVE");
    }

    @Test
    @Order(4)
    void derivedRecordsAreMaterializedPerIndicator() {
        // hash_records 寫入(04 表 6):每筆新 indicator 恰一列平台計算(source_id null)的
        // SHA256 記錄,digest 與 indicators.fingerprint 一致(指紋對 normalized_value 計算)
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM indicators i WHERE i.id NOT IN (SELECT id FROM e2e_indicator_snapshot)"
                                + " AND 1 <> (SELECT count(*) FROM hash_records h WHERE h.indicator_id = i.id"
                                + " AND h.algorithm = 'SHA256' AND h.source_id IS NULL AND h.digest = i.fingerprint)",
                        Integer.class))
                .isZero();

        // STIX 投影(§7.8、表 8):每筆新 indicator 恰一列 stix_objects,
        // stix_id = indicator--{id},tlp/owner 與 indicator 一致,content 含 pattern 與 marking 引用
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM indicators i WHERE i.id NOT IN (SELECT id FROM e2e_indicator_snapshot)"
                                + " AND 1 <> (SELECT count(*) FROM stix_objects o WHERE o.indicator_id = i.id"
                                + " AND o.stix_id = 'indicator--' || i.id AND o.stix_type = 'indicator'"
                                + " AND o.tlp = i.tlp AND o.owner_tenant_id = i.owner_tenant_id"
                                + " AND o.content ->> 'pattern' IS NOT NULL"
                                + " AND o.content -> 'object_marking_refs' IS NOT NULL)",
                        Integer.class))
                .isZero();
    }

    @Test
    @Order(5)
    void resyncUpsertsWithoutDuplicatingIndicators() {
        int indicatorsAfterFirstRun = newIndicatorCount();
        jdbc.update("UPDATE sources SET last_sync_at = now() - interval '2 hours', last_success_at = NULL,"
                + " next_cursor = NULL WHERE syncable = true AND enabled = true");

        List<SourceSyncOutcome> outcomes = sourceSyncService.syncDueSources();

        assertThat(outcomes).hasSize(3).allMatch(SourceSyncOutcome::success);
        // 全量重抓:每一筆接受的記錄都命中既有 indicator → merged == accepted(同來源 UPSERT,跨來源不覆寫)
        assertThat(outcomes).allSatisfy(o -> assertThat(o.recordsMerged()).isEqualTo(o.recordsAccepted()));
        assertThat(newIndicatorCount()).isEqualTo(indicatorsAfterFirstRun);

        // 同來源再次回報:reportCount 遞增(UPSERT 語意)
        assertThat(jdbc.queryForObject(
                        "SELECT s.report_count FROM indicator_sources s"
                                + " JOIN indicators i ON i.id = s.indicator_id"
                                + " JOIN sources src ON src.id = s.source_id"
                                + " WHERE i.normalized_value = 'shared-phish-1.example.com'"
                                + " AND src.source_type = 'MOCK_OPENPHISH'",
                        Integer.class))
                .isEqualTo(2);

        // 重同步不得重複物化 hash_records(reconcile 依 (algorithm, digest) 冪等)
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM hash_records h WHERE h.indicator_id NOT IN"
                                + " (SELECT id FROM e2e_indicator_snapshot)",
                        Integer.class))
                .isEqualTo(indicatorsAfterFirstRun);

        // STIX 投影以 stix_id UPSERT:重同步後仍一 indicator 一列,modified 前進、created 保持
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM stix_objects o WHERE o.indicator_id NOT IN"
                                + " (SELECT id FROM e2e_indicator_snapshot)",
                        Integer.class))
                .isEqualTo(indicatorsAfterFirstRun);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM stix_objects WHERE stix_created > stix_modified", Integer.class))
                .isZero();
    }

    @Test
    @Order(6)
    void stixEndpointsServeProjectionsWithVisibilityFiltering() throws Exception {
        // marking-definition 由 OASIS 常數供應,匿名可取
        mvc.perform(get("/api/v1/stix/marking-definition--94868c89-83c2-464b-929b-a1a8aa3c8487"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("TLP:CLEAR"))
                .andExpect(jsonPath("$.created").value("2022-10-01T00:00:00.000Z"));

        // CLEAR 的 indicator 投影:匿名可取,內容即落庫 content
        String clearStixId = "indicator--"
                + jdbc.queryForObject(
                        "SELECT id FROM indicators WHERE normalized_value = 'shared-phish-1.example.com'",
                        String.class);
        mvc.perform(get("/api/v1/stix/" + clearStixId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(clearStixId))
                .andExpect(jsonPath("$.pattern").value("[domain-name:value = 'shared-phish-1.example.com']"))
                .andExpect(jsonPath("$.object_marking_refs[0]")
                        .value("marking-definition--94868c89-83c2-464b-929b-a1a8aa3c8487"));

        // GREEN 的 indicator:匿名僅見 public CLEAR(07 §7.7)→ 404,不洩漏存在性
        String greenStixId = "indicator--"
                + jdbc.queryForObject(
                        "SELECT id FROM indicators WHERE normalized_value = '198.51.100.23'", String.class);
        mvc.perform(get("/api/v1/stix/" + greenStixId)).andExpect(status().isNotFound());

        // 查無 → 404;bundle 匿名無 stix:export → 403(10 §10.6;ADR 0005)
        mvc.perform(get("/api/v1/stix/indicator--3c9d8e7f-6b2a-4d5e-a1b2-c3d4e5f60718"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/stix/bundle")).andExpect(status().isForbidden());
    }

    private boolean normalizedExists(String normalizedValue) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM indicators WHERE normalized_value = ?", Integer.class, normalizedValue);
        return count != null && count == 1;
    }

    private int newIndicatorCount() {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM indicators WHERE id NOT IN (SELECT id FROM e2e_indicator_snapshot)",
                Integer.class);
        return count == null ? -1 : count;
    }
}
