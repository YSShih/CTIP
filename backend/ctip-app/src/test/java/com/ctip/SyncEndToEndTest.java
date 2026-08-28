package com.ctip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ctip.application.bloom.BloomChangeTracker;
import com.ctip.application.bloom.BloomDeltaService;
import com.ctip.application.bloom.BloomScopePlanner;
import com.ctip.application.bloom.BloomSnapshotService;
import com.ctip.application.bloom.BloomTarget;
import com.ctip.application.bloom.DeltaOutcome;
import com.ctip.application.identity.AuthService;
import com.ctip.application.identity.AuthSession;
import com.ctip.application.port.BloomVersionRepository;
import com.ctip.application.port.ClockPort;
import com.ctip.application.port.IdGeneratorPort;
import com.ctip.application.port.IndicatorRepository;
import com.ctip.application.port.PlanRepository;
import com.ctip.application.port.SourceRepository;
import com.ctip.application.port.SubscriptionRepository;
import com.ctip.application.port.TenantMembershipRepository;
import com.ctip.application.rbac.RoleCode;
import com.ctip.domain.bloom.BloomBitArray;
import com.ctip.domain.bloom.BloomScope;
import com.ctip.domain.bloom.BloomVersion;
import com.ctip.domain.plan.PlanCode;
import com.ctip.domain.source.SourceId;
import com.ctip.domain.tenant.TenantId;
import com.ctip.sdk.RedistributionPolicy;
import com.ctip.sdk.SourceType;
import com.ctip.sdk.Tlp;
import com.ctip.support.BloomFixtures;
import com.ctip.support.IndicatorFixtures;
import com.ctip.support.SyncFlowSteps;
import com.ctip.support.SyncTestClient;
import com.ctip.support.TestIdentities;
import com.ctip.support.TestPlans;
import java.time.Instant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * DoD M2-15 / M2-16 / M2-17:增量同步端對端(docs/spec/11-sync-bloom.md §11.5–§11.7、09 §9.1)。
 *
 * <p>主測試逐字走 §11.6 的 client 流程:manifest → 下載 full → 驗 checksum → 取 delta →
 * base64url 解碼 → 套用 → 驗 {@code resultingChecksum} → 更新版本,最後確認伺服器端與
 * 「更新後的版本」一致(再取一次 delta 得到空區間)。
 *
 * <p>另含 409 SNAPSHOT_REQUIRED 的兩個入口(鏈過長、base 不在現行 dataset)。
 * 授權與頻率限制那些「該被擋下來」的路徑在 {@link SyncAuthorizationTest}。
 * 排程在整合測試中關閉({@code SCHEDULER_ENABLED=false}),資料一律由測試自行呼叫生成服務準備。
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SyncEndToEndTest extends AbstractPostgresIntegrationTest {

    /** 每個測試各用自己的 IP:限流器與同步節流都在記憶體中跨測試類共用。 */
    private static final String FLOW_IP = "10.30.0.16";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private BloomSnapshotService snapshots;

    @Autowired
    private BloomDeltaService deltas;

    @Autowired
    private BloomScopePlanner planner;

    @Autowired
    private BloomVersionRepository versions;

    @Autowired
    private BloomChangeTracker changes;

    @Autowired
    private IndicatorRepository indicators;

    @Autowired
    private SourceRepository sources;

    @Autowired
    private AuthService authService;

    @Autowired
    private TenantMembershipRepository memberships;

    @Autowired
    private PlanRepository plans;

    @Autowired
    private SubscriptionRepository subscriptions;

    @Autowired
    private IdGeneratorPort idGenerator;

    @Autowired
    private ClockPort clock;

    private TestIdentities identities;
    private TestPlans planAdmin;
    private SourceId sourceId;

    @BeforeAll
    void prepare() {
        identities = new TestIdentities(authService, memberships);
        planAdmin = new TestPlans(plans, subscriptions, idGenerator, clock);
        sourceId = sources.findBySourceType(SourceType.MOCK_OPENPHISH)
                .orElseThrow()
                .id();
        // manifest 的 public 區塊要有內容:排程關閉,第一份 public snapshot 由測試自己產生
        snapshots.generate(planner.publicTarget());
    }

    /**
     * M2-16:完整同步流程。
     *
     * <p>間隔暫時改為 0 —— 頻率限制有自己的測試,而 PREMIUM 的 300 秒會讓這條流程在
     * 「下載 full」之後就被自己的節流擋住,測不到後面的 delta 套用。
     */
    @Test
    void theFullClientSyncFlowEndsAtTheChecksumTheManifestAdvertises() throws Exception {
        planAdmin.withPlan(PlanCode.PREMIUM, TestPlans.minSyncIntervalSeconds(0), () -> {
            AuthSession session = subscriber("sync-flow@example.org", PlanCode.PREMIUM);
            SyncTestClient client = SyncTestClient.of(mvc, json, FLOW_IP, session);
            BloomTarget target = tenantTarget(session);
            BloomVersion full = snapshots.generate(target);

            JsonNode advertised = SyncFlowSteps.assertManifestDescribes(client, full);
            BloomBitArray local = SyncFlowSteps.downloadAndVerify(client, full, advertised);

            assertThat(versions.findLatestFullSnapshot(BloomScope.TENANT, target.tenantId())
                            .orElseThrow()
                            .artifact()
                            .downloadCount())
                    .as("04 表 23 的 download_count:有了下載端點它才有呼叫端(規則 16)")
                    .isEqualTo(1);

            addMember(target.tenantId(), "0000e001", "sync-flow-added");
            assertThat(deltas.generate(target).status()).isEqualTo(DeltaOutcome.Status.CREATED);

            SyncFlowSteps.applyDeltaAndVerify(client, full, local);
            SyncFlowSteps.assertServerAgreesWithTheUpdatedVersion(client, local);
        });
    }

    /** M2-17:manifest 必含 coverage 與 notCovered——client 開發者要在 manifest 就看到覆蓋範圍限制。 */
    @Test
    void theManifestAlwaysDisclosesCoverageAndWhatIsNotCovered() throws Exception {
        AuthSession session = subscriber("sync-coverage@example.org", PlanCode.PREMIUM);
        snapshots.generate(tenantTarget(session));

        JsonNode manifest = SyncTestClient.of(mvc, json, FLOW_IP, session).manifest();

        assertThat(manifest.get("public").get("coverage").asString()).isEqualTo("TLP:CLEAR only");
        assertThat(manifest.get("tenant").get("coverage").asString())
                .isEqualTo("TLP:AMBER, TLP:AMBER_STRICT of your tenant");
        assertThat(manifest.get("notCovered").toString())
                .as("TLP:GREEN 沒有任何 Bloom 覆蓋(§11.1)")
                .isEqualTo("[\"TLP:GREEN\"]");
        assertThat(manifest.get("maxDeltaChain").asInt()).isEqualTo(24);
    }

    /**
     * M2-15:delta 鏈超過上限時回 409 SNAPSHOT_REQUIRED。
     *
     * <p>鏈長由 {@code BLOOM_MAX_DELTA_CHAIN}(預設 24)決定,生成端在鏈已達上限後就不再追加,
     * 因此這裡真的產生 25 段 delta,讓 {@code chainLength > 24} 在資料庫裡成立
     * ——與生成端呼叫的是同一個 {@code BloomVersion.requiresFullSnapshot}。
     *
     * <p>並確認 409 <strong>不</strong>消耗同步間隔:client 收到它之後照 §11.6 必須改下載 full,
     * 若 409 也記帳,那一步會立刻撞上 429,整條復原路徑就永遠走不完。
     */
    @Test
    void aDeltaChainBeyondTheLimitAsksForAFullSnapshot() throws Exception {
        AuthSession session = subscriber("sync-chain@example.org", PlanCode.PREMIUM);
        SyncTestClient client = SyncTestClient.of(mvc, json, FLOW_IP, session);
        BloomTarget target = tenantTarget(session);
        snapshots.generate(target);
        for (int i = 0; i <= 24; i++) {
            addMember(target.tenantId(), "0000e1%02x".formatted(i), "sync-chain-" + i);
            assertThat(deltas.generate(target).status()).as("第 %d 段 delta", i).isEqualTo(DeltaOutcome.Status.CREATED);
        }

        client.deltaRequest("?base=0&scope=TENANT")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SNAPSHOT_REQUIRED"))
                .andExpect(jsonPath("$.message").value("Delta chain too long, download full snapshot"));

        client.bloomRequest("?scope=TENANT").andExpect(status().isOk());
    }

    /** 另一個 409 入口:client 的 base 不在現行 dataset 的鏈上(通常是它的本地版本屬於舊 dataset)。 */
    @Test
    void aBaseVersionOutsideTheCurrentDatasetAsksForAFullSnapshot() throws Exception {
        AuthSession session = subscriber("sync-base@example.org", PlanCode.PREMIUM);
        snapshots.generate(tenantTarget(session));

        SyncTestClient.of(mvc, json, FLOW_IP, session)
                .deltaRequest("?base=999&scope=TENANT")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SNAPSHOT_REQUIRED"));
    }

    private AuthSession subscriber(String email, PlanCode code) {
        AuthSession session = identities.register(email, RoleCode.TENANT_ADMIN);
        planAdmin.assign(session.identity().tenantId(), code);
        return session;
    }

    private BloomTarget tenantTarget(AuthSession session) {
        return planner.tenantTarget(session.identity().tenantId()).orElseThrow();
    }

    /** 觀測時間必須晚於上一次生成,否則落在 delta 的水位之外(§11.3)。 */
    private void addMember(TenantId owner, String id, String name) {
        Instant seen = clock.now().plusSeconds(300);
        BloomFixtures.upsertSeenAt(
                indicators,
                sourceId,
                new IndicatorFixtures.Fixture(
                        BloomFixtures.id(id), owner, Tlp.AMBER, RedistributionPolicy.INTERNAL_ONLY, name),
                seen);
        changes.markChanged(BloomScope.TENANT, owner);
    }
}
