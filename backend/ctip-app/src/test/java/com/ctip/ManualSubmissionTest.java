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
import com.ctip.application.port.PlanRepository;
import com.ctip.application.port.SubscriptionRepository;
import com.ctip.application.port.TenantMembershipRepository;
import com.ctip.application.rbac.RoleCode;
import com.ctip.domain.plan.PlanCode;
import com.ctip.domain.tenant.TenantId;
import com.ctip.support.TestIdentities;
import com.ctip.support.TestPlans;
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
 * 手動提交(docs/spec/09-api.md §9.7 {@code POST /api/v1/iocs};phase-14 完成判準)。
 *
 * <p>判準明列必須驗證的三件事:預設 {@code TLP:AMBER}、走完整 pipeline(含驗證與去重)、
 * 擁有租戶看得到自己的資料(再散布過濾不作用於自己)。另加上不可指定歸屬與 publish 語意。
 */
@AutoConfigureMockMvc
class ManualSubmissionTest extends AbstractPostgresIntegrationTest {

    private static final String CLIENT_IP = "10.20.0.41";

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

    /** 提交者以 PREMIUM 訂閱建立(FREE 的每日提交上限是 0,那是另一條測試)。 */
    private AuthSession premiumSubmitter(String email) {
        AuthSession session = identities.register(email, RoleCode.TENANT_ADMIN);
        planAdmin.assign(session.identity().tenantId(), PlanCode.PREMIUM);
        return session;
    }

    private JsonNode submit(AuthSession submitter, String body, int expectedStatus) throws Exception {
        String response = mvc.perform(asClient(post("/api/v1/iocs")
                        .header("Authorization", TestIdentities.bearer(submitter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)))
                .andExpect(status().is(expectedStatus))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return json.readTree(response);
    }

    @Test
    void defaultsToAmberAndIsOwnedBySubmitterTenant() throws Exception {
        AuthSession submitter = premiumSubmitter("submit-default@example.org");

        JsonNode created = submit(submitter, "{\"type\":\"IPV4\",\"value\":\"198.51.100.21\"}", 201);

        assertThat(created.get("tlp").asString()).isEqualTo("AMBER");
        // 歸屬不可由呼叫端指定:回應中的 IOC 必須屬於提交者的租戶,且對他自己可見
        TenantId owner = submitter.identity().tenantId();
        mvc.perform(asClient(get("/api/v1/iocs/" + created.get("id").asString())
                        .header("Authorization", TestIdentities.bearer(submitter))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value("198.51.100.21"));
        assertThat(owner.isPublic()).isFalse();
    }

    /**
     * 再散布過濾不作用於自己:MANUAL 來源固定 {@code INTERNAL_ONLY}(I14),
     * 若擁有租戶豁免失效,提交者連自己剛送出的 IOC 都會看不到。
     */
    @Test
    void ownerSeesItsOwnInternalOnlyIocInListingsAndSourceDetail() throws Exception {
        AuthSession submitter = premiumSubmitter("submit-visible@example.org");
        JsonNode created = submit(
                submitter, "{\"type\":\"DOMAIN\",\"value\":\"owner-visible.example.org\",\"tags\":[\"owned\"]}", 201);
        String id = created.get("id").asString();

        mvc.perform(asClient(get("/api/v1/iocs?tags=owned").header("Authorization", TestIdentities.bearer(submitter))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(id));
        mvc.perform(asClient(get("/api/v1/iocs/" + id + "/sources")
                        .header("Authorization", TestIdentities.bearer(submitter))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sourceName").value("Manual Submission"))
                .andExpect(jsonPath("$[0].redistributionPolicy").value("INTERNAL_ONLY"));
    }

    /** 另一個租戶看不到:INTERNAL_ONLY + AMBER 的私有資料絕不得外流(§7.9 規則 3)。 */
    @Test
    void otherTenantCannotSeeTheSubmission() throws Exception {
        AuthSession submitter = premiumSubmitter("submit-private@example.org");
        JsonNode created = submit(submitter, "{\"type\":\"IPV4\",\"value\":\"198.51.100.22\"}", 201);
        AuthSession stranger = identities.register("submit-stranger@example.org", RoleCode.TENANT_ADMIN);

        mvc.perform(asClient(get("/api/v1/iocs/" + created.get("id").asString())
                        .header("Authorization", TestIdentities.bearer(stranger))))
                .andExpect(status().isNotFound());
    }

    /** 走完整 pipeline:私有 IP 由 ValidateStage 拒絕,不因為是手動提交就放行(§7.3)。 */
    @Test
    void pipelineValidationIsNotBypassed() throws Exception {
        AuthSession submitter = premiumSubmitter("submit-private-ip@example.org");

        JsonNode error = submit(submitter, "{\"type\":\"IPV4\",\"value\":\"10.0.0.1\"}", 400);

        assertThat(error.get("code").asString()).isEqualTo("INVALID_IOC_FORMAT");
        assertThat(error.get("message").asString()).contains("PRIVATE_OR_RESERVED_IP");
    }

    /** 走完整 pipeline:同一值再次提交會命中去重並合併,回 200 而非 201(§9.7)。 */
    @Test
    void resubmissionMergesInsteadOfCreatingDuplicate() throws Exception {
        AuthSession submitter = premiumSubmitter("submit-dedup@example.org");
        JsonNode first = submit(submitter, "{\"type\":\"DOMAIN\",\"value\":\"Dedup-Case.Example.ORG.\"}", 201);

        JsonNode second = submit(submitter, "{\"type\":\"DOMAIN\",\"value\":\"dedup-case.example.org\"}", 200);

        assertThat(second.get("id").asString()).isEqualTo(first.get("id").asString());
    }

    /** CLEAR/GREEN 需要 ioc:publish;TENANT_ADMIN 沒有該權限(§10.3 矩陣)。 */
    @Test
    void publishingRequiresIocPublishPermission() throws Exception {
        AuthSession submitter = premiumSubmitter("submit-publish@example.org");

        JsonNode error = submit(submitter, "{\"type\":\"IPV4\",\"value\":\"198.51.100.23\",\"tlp\":\"CLEAR\"}", 403);

        assertThat(error.get("code").asString()).isEqualTo("FORBIDDEN");
    }

    /** TLP:RED 不進入平台(07);在服務層就擋掉,不落庫。 */
    @Test
    void redIsRejected() throws Exception {
        AuthSession submitter = premiumSubmitter("submit-red@example.org");

        submit(submitter, "{\"type\":\"IPV4\",\"value\":\"198.51.100.24\",\"tlp\":\"RED\"}", 400);
    }

    /**
     * {@code ioc:publish} 的語意是擁有權轉移(ADR 0019 第 2 節):
     * 落 public tenant 才會真的公開——否則那個權限不產生任何公開效果。
     */
    @Test
    void publishTransfersOwnershipToThePublicTenant() throws Exception {
        AuthSession admin = identities.register("submit-sysadmin@example.org", RoleCode.SYSTEM_ADMIN);
        planAdmin.assign(admin.identity().tenantId(), PlanCode.PREMIUM);

        JsonNode created =
                submit(admin, "{\"type\":\"DOMAIN\",\"value\":\"published.example.org\",\"tlp\":\"CLEAR\"}", 201);

        assertThat(created.get("tlp").asString()).isEqualTo("CLEAR");
        // 匿名(綁 public tenant、只看得到 CLEAR)看得到 = 真的進了公開情資池
        mvc.perform(asClient(get("/api/v1/iocs/" + created.get("id").asString())))
                .andExpect(status().isOk());
    }

    private static MockHttpServletRequestBuilder asClient(MockHttpServletRequestBuilder builder) {
        return builder.with(request -> {
            request.setRemoteAddr(CLIENT_IP);
            return request;
        });
    }
}
