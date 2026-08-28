package com.ctip;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ctip.application.identity.AuthService;
import com.ctip.application.identity.AuthSession;
import com.ctip.application.port.ClockPort;
import com.ctip.application.port.IdGeneratorPort;
import com.ctip.application.port.PlanRepository;
import com.ctip.application.port.SubscriptionRepository;
import com.ctip.application.port.TenantMembershipRepository;
import com.ctip.application.rbac.RoleCode;
import com.ctip.domain.plan.PlanCode;
import com.ctip.support.TestIdentities;
import com.ctip.support.TestPlans;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

/**
 * 方案配額的強制(docs/spec/10-identity-plans.md §10.6、09 §9.7;phase-14 完成判準)。
 *
 * <p>涵蓋 §9.7 的四種語意各自的出口:夾到上限(分頁)、413(單次尺寸)、
 * 403 PLAN_LIMIT_EXCEEDED(非時間窗的能力上限)、429(時間窗內的計數)。
 * 所有數值一律來自 {@code plans} 表——測試改的是表,不是任何 property。
 */
@AutoConfigureMockMvc
class QuotaEnforcementTest extends AbstractPostgresIntegrationTest {

    private static final String CLIENT_IP = "10.20.0.43";

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

    private AuthSession subscriber(String email, PlanCode code) {
        AuthSession session = identities.register(email, RoleCode.TENANT_ADMIN);
        planAdmin.assign(session.identity().tenantId(), code);
        return session;
    }

    /** §9.3:分頁上限夾到方案上限,不報錯。匿名綁 public tenant → ANONYMOUS 的 50。 */
    @Test
    void pageSizeIsClampedToThePlanLimit() throws Exception {
        mvc.perform(asClient(get("/api/v1/iocs?limit=500")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(50));
    }

    /** 已登入的 FREE 租戶上限 100,比匿名高——夾值必須依身分查表,不是固定常數。 */
    @Test
    void pageSizeLimitFollowsTheCallersPlan() throws Exception {
        AuthSession free = identities.register("quota-page@example.org", RoleCode.TENANT_ADMIN);

        mvc.perform(asClient(get("/api/v1/iocs?limit=500").header("Authorization", TestIdentities.bearer(free))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(100));
    }

    /** §9.7:單次請求的尺寸上限 → 413(匿名 max_batch_lookup = 20)。 */
    @Test
    void batchLookupBeyondPlanLimitIsPayloadTooLarge() throws Exception {
        String values = IntStream.rangeClosed(1, 21)
                .mapToObj(i -> "\"198.51.100." + i + "\"")
                .collect(Collectors.joining(",", "[", "]"));

        mvc.perform(asClient(post("/api/v1/iocs/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"values\":" + values + "}")))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
    }

    /** §9.7:非時間窗的能力上限 → 403 PLAN_LIMIT_EXCEEDED(FREE 的 max_api_keys = 1)。 */
    @Test
    void apiKeyCountBeyondPlanLimitIsPlanLimitExceeded() throws Exception {
        AuthSession free = identities.register("quota-apikey@example.org", RoleCode.TENANT_ADMIN);
        createKey(free, "first", 201);

        mvc.perform(asClient(post("/api/v1/api-keys")
                        .header("Authorization", TestIdentities.bearer(free))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"second\",\"scopes\":[\"ioc:read\"]}")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PLAN_LIMIT_EXCEEDED"));
    }

    /** PREMIUM 的 max_api_keys = 10:同一段程式在不同方案下必須有不同上限。 */
    @Test
    void apiKeyLimitFollowsThePlan() throws Exception {
        AuthSession premium = subscriber("quota-apikey-premium@example.org", PlanCode.PREMIUM);

        createKey(premium, "first", 201);
        createKey(premium, "second", 201);
    }

    /**
     * §9.7:方案未開放的能力 → 403 PLAN_LIMIT_EXCEEDED。
     * FREE 的 {@code max_manual_submissions_per_day} 是 0,而自助註冊即得 TENANT_ADMIN
     * (含 {@code ioc:submit},ADR 0012 決策 5)——這道檢查是唯一阻止免費取得提交能力的閘門。
     */
    @Test
    void freePlanCannotSubmitAtAll() throws Exception {
        AuthSession free = identities.register("quota-free-submit@example.org", RoleCode.TENANT_ADMIN);

        mvc.perform(asClient(post("/api/v1/iocs")
                        .header("Authorization", TestIdentities.bearer(free))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"IPV4\",\"value\":\"198.51.100.31\"}")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PLAN_LIMIT_EXCEEDED"));
    }

    /** §9.7:時間窗內的計數用罄 → 429 + X-RateLimit-* + Retry-After。 */
    @Test
    void dailySubmissionQuotaExhaustionIsRateLimited() throws Exception {
        AuthSession premium = subscriber("quota-daily@example.org", PlanCode.PREMIUM);

        planAdmin.withPlan(PlanCode.PREMIUM, TestPlans.manualSubmissionsPerDay(2), () -> {
            submitOk(premium, "198.51.100.41");
            submitOk(premium, "198.51.100.42");
            mvc.perform(asClient(post("/api/v1/iocs")
                            .header("Authorization", TestIdentities.bearer(premium))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"type\":\"IPV4\",\"value\":\"198.51.100.43\"}")))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"))
                    .andExpect(header().string("X-RateLimit-Limit", "2"))
                    .andExpect(header().exists("Retry-After"));
        });
    }

    /** §9.7:單檔匯入筆數上限 → 413。 */
    @Test
    void importBeyondRowLimitIsPayloadTooLarge() throws Exception {
        AuthSession premium = subscriber("quota-import@example.org", PlanCode.PREMIUM);
        String csv = "type,value\nDOMAIN,a.example.org\nDOMAIN,b.example.org\n";

        planAdmin.withPlan(
                PlanCode.PREMIUM,
                TestPlans.importRowsPerFile(1),
                () -> mvc.perform(asClient(post("/api/v1/iocs/import")
                                .header("Authorization", TestIdentities.bearer(premium))
                                .contentType("text/csv")
                                .content(csv)))
                        .andExpect(status().isPayloadTooLarge())
                        .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE")));
    }

    /** FREE 的 max_import_rows_per_file = 0 是「停用」而非「上限 0」→ 403(§9.7、ADR 0019)。 */
    @Test
    void freePlanCannotImport() throws Exception {
        AuthSession free = identities.register("quota-free-import@example.org", RoleCode.TENANT_ADMIN);

        mvc.perform(asClient(post("/api/v1/iocs/import")
                        .header("Authorization", TestIdentities.bearer(free))
                        .contentType("text/csv")
                        .content("type,value\nDOMAIN,free-import.example.org\n")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PLAN_LIMIT_EXCEEDED"));
    }

    /** 匿名的 stix_export_max_objects = 0,但匿名連 stix:export 權限都沒有 → 先被 403 擋下。 */
    @Test
    void stixExportLimitComesFromThePlan() throws Exception {
        AuthSession free = identities.register("quota-stix@example.org", RoleCode.TENANT_ADMIN);

        // FREE 上限 1000;樣本資料的可匯出物件數遠低於此,故成功
        mvc.perform(asClient(get("/api/v1/stix/bundle").header("Authorization", TestIdentities.bearer(free))))
                .andExpect(status().isOk());

        // 把 FREE 的上限降到 1 → 同一個請求變成 403 PLAN_LIMIT_EXCEEDED
        planAdmin.withPlan(
                PlanCode.FREE,
                TestPlans.stixExportMaxObjects(1),
                () -> mvc.perform(asClient(
                                get("/api/v1/stix/bundle").header("Authorization", TestIdentities.bearer(free))))
                        .andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.code").value("PLAN_LIMIT_EXCEEDED")));
    }

    /** 用量端點:已用量與上限都必須來自實際的計數與方案,不是回傳輸入值。 */
    @Test
    void usageEndpointReportsRealConsumption() throws Exception {
        AuthSession premium = subscriber("quota-usage@example.org", PlanCode.PREMIUM);
        submitOk(premium, "198.51.100.51");

        mvc.perform(asClient(get("/api/v1/subscription/usage").header("Authorization", TestIdentities.bearer(premium))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planCode").value("PREMIUM"))
                .andExpect(jsonPath("$.manualSubmissionsToday.used").value(1))
                .andExpect(jsonPath("$.manualSubmissionsToday.limit").value(1000))
                .andExpect(jsonPath("$.apiKeys.used").value(0));
    }

    /** 沒有訂閱的已登入租戶視為 FREE(不變量 B4);ENTERPRISE 的無限制以 null 表達。 */
    @Test
    void subscriptionEndpointReportsTheEffectivePlan() throws Exception {
        AuthSession free = identities.register("quota-sub-free@example.org", RoleCode.TENANT_ADMIN);
        AuthSession enterprise = subscriber("quota-sub-ent@example.org", PlanCode.ENTERPRISE);

        mvc.perform(asClient(get("/api/v1/subscription").header("Authorization", TestIdentities.bearer(free))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planCode").value("FREE"))
                .andExpect(jsonPath("$.status").doesNotExist());

        mvc.perform(asClient(get("/api/v1/subscription").header("Authorization", TestIdentities.bearer(enterprise))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planCode").value("ENTERPRISE"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.quotas.requestsPerDay").doesNotExist())
                .andExpect(jsonPath("$.quotas.stixExportMaxObjects").doesNotExist());
    }

    private void submitOk(AuthSession submitter, String ip) throws Exception {
        mvc.perform(asClient(post("/api/v1/iocs")
                        .header("Authorization", TestIdentities.bearer(submitter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"IPV4\",\"value\":\"" + ip + "\"}")))
                .andExpect(status().isCreated());
    }

    private void createKey(AuthSession session, String name, int expectedStatus) throws Exception {
        mvc.perform(asClient(post("/api/v1/api-keys")
                        .header("Authorization", TestIdentities.bearer(session))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"scopes\":[\"ioc:read\"]}")))
                .andExpect(status().is(expectedStatus));
    }

    private static MockHttpServletRequestBuilder asClient(MockHttpServletRequestBuilder builder) {
        return builder.with(request -> {
            request.setRemoteAddr(CLIENT_IP);
            return request;
        });
    }
}
