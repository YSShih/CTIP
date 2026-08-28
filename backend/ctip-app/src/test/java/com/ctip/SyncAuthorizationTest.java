package com.ctip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ctip.application.bloom.BloomScopePlanner;
import com.ctip.application.bloom.BloomSnapshotService;
import com.ctip.application.port.ClockPort;
import com.ctip.application.port.IdGeneratorPort;
import com.ctip.application.port.PlanRepository;
import com.ctip.application.port.SubscriptionRepository;
import com.ctip.domain.plan.PlanCode;
import com.ctip.support.SyncTestClient;
import com.ctip.support.TestPlans;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/**
 * 同步端點的授權與頻率限制(docs/spec/11-sync-bloom.md §11.5 「下載授權依方案」、§11.6;09 §9.7)。
 *
 * <p>與 {@link SyncEndToEndTest} 分開,是因為那一支跑的是「一切正常時的完整流程」,
 * 這一支跑的是「該被擋下來的路徑」——兩者的前置條件相反(前者刻意把同步間隔改成 0)。
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SyncAuthorizationTest extends AbstractPostgresIntegrationTest {

    /** 每個測試各用自己的 IP:限流器與同步節流都在記憶體中跨測試類共用。 */
    private static final String ANONYMOUS_IP = "10.30.0.21";

    private static final String THROTTLE_IP = "10.30.0.22";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private BloomSnapshotService snapshots;

    @Autowired
    private BloomScopePlanner planner;

    @Autowired
    private PlanRepository plans;

    @Autowired
    private SubscriptionRepository subscriptions;

    @Autowired
    private IdGeneratorPort idGenerator;

    @Autowired
    private ClockPort clock;

    private TestPlans planAdmin;

    @BeforeAll
    void prepare() {
        planAdmin = new TestPlans(plans, subscriptions, idGenerator, clock);
        // 排程關閉,public snapshot 由測試自己產生
        snapshots.generate(planner.publicTarget());
    }

    /** 匿名沒有 tenant 那一層:manifest 不得出現它,直接要下載也不行。 */
    @Test
    void anonymousCallersHaveNoTenantLayer() throws Exception {
        SyncTestClient anonymous = SyncTestClient.anonymous(mvc, json, ANONYMOUS_IP);

        anonymous
                .manifestRequest()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.public").exists())
                .andExpect(jsonPath("$.tenant").doesNotExist())
                .andExpect(jsonPath("$.notCovered[0]").value("TLP:GREEN"));

        anonymous
                .bloomRequest("?scope=TENANT")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PLAN_LIMIT_EXCEEDED"));

        // sync:delta 匿名不持有(§10.3 矩陣)
        anonymous
                .deltaRequest("?base=0")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    /**
     * §11.6:同步頻率受 {@code plans.min_sync_interval_seconds} 限制,過於頻繁回 429。
     *
     * <p>用匿名身分測:ANONYMOUS 的間隔是 86,400 秒且持有 {@code sync:bloom},
     * 不必改任何方案值就能觀察到第二次被擋。記帳對象是正規化後的 client IP(§10.7 維度 4)。
     */
    @Test
    void syncingAgainBeforeTheIntervalElapsesIsRateLimited() throws Exception {
        SyncTestClient client = SyncTestClient.anonymous(mvc, json, THROTTLE_IP);

        client.bloomRequest("").andExpect(status().isOk());

        client.bloomRequest("")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"))
                .andExpect(
                        result -> assertThat(Long.parseLong(result.getResponse().getHeader("Retry-After")))
                                .isBetween(1L, 86_400L));

        // manifest 不受同步間隔限制:client 必須能持續確認自己的版本是否已作廢(§11.6 第 1–2 步)
        client.manifestRequest().andExpect(status().isOk());
    }

    /** 方案關掉 public Bloom 時,manifest 不得宣傳它,下載也必須擋掉(§11.5 下載授權依方案)。 */
    @Test
    void aPlanWithoutPublicBloomNeitherAdvertisesNorServesIt() throws Exception {
        SyncTestClient client = SyncTestClient.anonymous(mvc, json, ANONYMOUS_IP);
        planAdmin.withPlan(PlanCode.ANONYMOUS, TestPlans.publicBloomEnabled(false), () -> {
            client.manifestRequest()
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.public").doesNotExist());

            client.bloomRequest("?scope=PUBLIC")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("PLAN_LIMIT_EXCEEDED"));
        });
    }
}
