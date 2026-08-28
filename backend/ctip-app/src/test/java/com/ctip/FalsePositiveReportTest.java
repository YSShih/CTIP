package com.ctip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ctip.application.identity.AuthService;
import com.ctip.application.identity.AuthSession;
import com.ctip.application.port.ClockPort;
import com.ctip.application.port.IdGeneratorPort;
import com.ctip.application.port.IndicatorRepository;
import com.ctip.application.port.PlanRepository;
import com.ctip.application.port.SourceRepository;
import com.ctip.application.port.SubscriptionRepository;
import com.ctip.application.port.TenantMembershipRepository;
import com.ctip.application.rbac.RoleCode;
import com.ctip.domain.indicator.Indicator;
import com.ctip.domain.indicator.IndicatorId;
import com.ctip.domain.indicator.IndicatorSource;
import com.ctip.domain.indicator.IndicatorStatus;
import com.ctip.domain.indicator.SourceRecordStatus;
import com.ctip.domain.plan.PlanCode;
import com.ctip.domain.source.Reputation;
import com.ctip.domain.source.SourceId;
import com.ctip.sdk.SourceType;
import com.ctip.support.TestIdentities;
import com.ctip.support.TestPlans;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 誤判回報(docs/spec/09-api.md §9.7;phase-14 完成判準)。
 *
 * <p>判準明列:最終 status 由 {@code IndicatorMergePolicy} 決定,<strong>而非呼叫端指定</strong>。
 * 另驗證作用域(只接受自家 IOC,公開情資回 403)與「來源記錄不存在則建立」。
 */
@AutoConfigureMockMvc
class FalsePositiveReportTest extends AbstractPostgresIntegrationTest {

    private static final String CLIENT_IP = "10.20.0.42";
    private static final String REPORT_BODY = "{\"reason\":\"legitimate CDN endpoint\"}";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private AuthService authService;

    @Autowired
    private TenantMembershipRepository memberships;

    @Autowired
    private PlanRepository plans;

    @Autowired
    private SubscriptionRepository subscriptions;

    @Autowired
    private IndicatorRepository indicators;

    @Autowired
    private SourceRepository sources;

    @Autowired
    private IdGeneratorPort idGenerator;

    @Autowired
    private ClockPort clock;

    private TestIdentities identities;
    private TestPlans planAdmin;

    @BeforeEach
    void setUp() {
        identities = new TestIdentities(authService, memberships);
        planAdmin = new TestPlans(plans, subscriptions, idGenerator, clock);
    }

    private AuthSession premiumSubmitter(String email) {
        AuthSession session = identities.register(email, RoleCode.TENANT_ADMIN);
        planAdmin.assign(session.identity().tenantId(), PlanCode.PREMIUM);
        return session;
    }

    private String submit(AuthSession submitter, String value) throws Exception {
        String response = mvc.perform(asClient(post("/api/v1/iocs")
                        .header("Authorization", TestIdentities.bearer(submitter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DOMAIN\",\"value\":\"" + value + "\"}")))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return json.readTree(response).get("id").asString();
    }

    private JsonNode report(AuthSession reporter, String id, int expectedStatus) throws Exception {
        String response = mvc.perform(asClient(post("/api/v1/iocs/" + id + "/report-false-positive")
                        .header("Authorization", TestIdentities.bearer(reporter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REPORT_BODY)))
                .andExpect(status().is(expectedStatus))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return json.readTree(response);
    }

    /** 只有 MANUAL 一個來源 → 沒有 ACTIVE 來源了 → 合併規則判為 FALSE_POSITIVE。 */
    @Test
    void statusBecomesFalsePositiveWhenNoActiveSourceRemains() throws Exception {
        AuthSession owner = premiumSubmitter("fp-owner@example.org");
        String id = submit(owner, "fp-single-source.example.org");

        JsonNode reported = report(owner, id, 200);

        assertThat(reported.get("status").asString()).isEqualTo("FALSE_POSITIVE");
    }

    /**
     * 判準的核心:呼叫端回報了誤判,但還有另一個 ACTIVE 來源——
     * 依 I11 規則 2 的短路求值,最終狀態仍是 ACTIVE。呼叫端不能指定結果。
     */
    @Test
    void statusIsDecidedByMergePolicyNotByTheCaller() throws Exception {
        AuthSession owner = premiumSubmitter("fp-multi-source@example.org");
        String id = submit(owner, "fp-two-sources.example.org");
        addSecondActiveSource(new IndicatorId(UUID.fromString(id)));

        JsonNode reported = report(owner, id, 200);

        assertThat(reported.get("status").asString()).isEqualTo("ACTIVE");
        // 但該來源的記錄確實被標成 FALSE_POSITIVE 了——只是不足以改變聚合狀態
        Indicator stored =
                indicators.findById(new IndicatorId(UUID.fromString(id))).orElseThrow();
        assertThat(stored.snapshot().sources())
                .anySatisfy(record -> assertThat(record.status()).isEqualTo(SourceRecordStatus.FALSE_POSITIVE));
        assertThat(stored.status()).isEqualTo(IndicatorStatus.ACTIVE);
    }

    /** §9.7 作用域:公開情資一律 403,並指向平台申訴流程(不是 404,那會誤導成「不存在」)。 */
    @Test
    void publicIntelligenceIsRejectedWithForbidden() throws Exception {
        AuthSession reporter = premiumSubmitter("fp-public@example.org");
        String publicIocId = anyPublicIndicatorId(reporter);

        JsonNode error = report(reporter, publicIocId, 403);

        assertThat(error.get("code").asString()).isEqualTo("FORBIDDEN");
    }

    /** 別的租戶的 IOC 不可見 → 404(不得洩漏資源存在性)。 */
    @Test
    void anotherTenantsIocIsNotFound() throws Exception {
        AuthSession owner = premiumSubmitter("fp-owner-2@example.org");
        String id = submit(owner, "fp-other-tenant.example.org");
        AuthSession stranger = premiumSubmitter("fp-stranger@example.org");

        report(stranger, id, 404);
    }

    /** 加一筆別的來源的 ACTIVE 記錄(來源同步的效果),用來測合併規則的判定。 */
    private void addSecondActiveSource(IndicatorId id) {
        Indicator indicator = indicators.findById(id).orElseThrow();
        SourceId otherSource = sources.findBySourceType(SourceType.MOCK_OPENPHISH)
                .orElseThrow()
                .id();
        var report = new com.ctip.domain.indicator.IndicatorSourceSnapshot(
                otherSource,
                indicator.value().raw(),
                null,
                null,
                indicator.tlp(),
                clock.now(),
                clock.now(),
                null,
                com.ctip.sdk.RedistributionPolicy.ATTRIBUTION_REQUIRED,
                1,
                SourceRecordStatus.ACTIVE,
                java.util.Set.of(),
                java.util.Map.of());
        indicator.mergeFrom(new IndicatorSource(report), new Reputation(70));
        indicators.save(indicator);
    }

    /** 樣本資料裡任一筆公開 CLEAR 情資。 */
    private String anyPublicIndicatorId(AuthSession session) throws Exception {
        String listing = mvc.perform(asClient(
                        get("/api/v1/iocs?tlp=CLEAR&limit=1").header("Authorization", TestIdentities.bearer(session))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return json.readTree(listing).at("/items/0/id").asString();
    }

    private static MockHttpServletRequestBuilder asClient(MockHttpServletRequestBuilder builder) {
        return builder.with(request -> {
            request.setRemoteAddr(CLIENT_IP);
            return request;
        });
    }
}
