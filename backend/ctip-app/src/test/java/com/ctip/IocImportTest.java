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
import com.ctip.support.TestIdentities;
import com.ctip.support.TestPlans;
import java.time.Duration;
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
 * 批次匯入(docs/spec/09-api.md §9.7 {@code POST /api/v1/iocs/import}):
 * 202 + jobId、非同步處理、進度查詢、CSV 與 STIX bundle 兩種格式、跨租戶不可見。
 *
 * <p>配額相關的出口(413 / 403 / 逐筆 QUOTA_EXCEEDED)在 {@code QuotaEnforcementTest}。
 */
@AutoConfigureMockMvc
class IocImportTest extends AbstractPostgresIntegrationTest {

    private static final String CLIENT_IP = "10.20.0.44";

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

    private AuthSession premium(String email) {
        AuthSession session = identities.register(email, RoleCode.TENANT_ADMIN);
        planAdmin.assign(session.identity().tenantId(), PlanCode.PREMIUM);
        return session;
    }

    private String accept(AuthSession importer, String contentType, String body) throws Exception {
        String response = mvc.perform(asClient(post("/api/v1/iocs/import")
                        .header("Authorization", TestIdentities.bearer(importer))
                        .contentType(contentType)
                        .content(body)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return json.readTree(response).get("importJobId").asString();
    }

    /**
     * 非同步:202 之後輪詢進度端點直到終態。
     *
     * <p>刻意不用固定 sleep:job 可能在毫秒內完成,也可能因為背景執行緒忙碌而慢一點,
     * 固定等待不是太慢就是不穩。逾時視為失敗——「一直沒有終態」本身就是缺陷。
     */
    private JsonNode awaitTerminal(AuthSession importer, String jobId) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        JsonNode latest = jobStatus(importer, jobId);
        while (!isTerminal(latest.get("status").asString())) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("匯入 job 在 20 秒內未到終態:" + latest);
            }
            Thread.sleep(100);
            latest = jobStatus(importer, jobId);
        }
        return latest;
    }

    private static boolean isTerminal(String status) {
        return "SUCCESS".equals(status) || "PARTIAL".equals(status) || "FAILURE".equals(status);
    }

    private JsonNode jobStatus(AuthSession importer, String jobId) throws Exception {
        String response = mvc.perform(asClient(
                        get("/api/v1/iocs/import/" + jobId).header("Authorization", TestIdentities.bearer(importer))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return json.readTree(response);
    }

    @Test
    void csvImportRunsThroughThePipelineAndReportsCounts() throws Exception {
        AuthSession importer = premium("import-csv@example.org");
        String csv = """
                type,value,confidence,severity,tags,note
                DOMAIN,import-one.example.org,80,HIGH,imported;csv,from incident 42
                IPV4,198.51.100.61,,MEDIUM,,
                IPV4,10.0.0.5,,,,
                """;

        String jobId = accept(importer, "text/csv", csv);
        JsonNode finished = awaitTerminal(importer, jobId);

        // 私有 IP 被 pipeline 拒絕 → 有拒絕筆數即 PARTIAL(不是整批失敗)
        assertThat(finished.get("status").asString()).isEqualTo("PARTIAL");
        assertThat(finished.get("totalRows").asInt()).isEqualTo(3);
        assertThat(finished.get("acceptedCount").asInt()).isEqualTo(2);
        assertThat(finished.get("rejectedCount").asInt()).isEqualTo(1);

        mvc.perform(asClient(
                        get("/api/v1/iocs?tags=imported").header("Authorization", TestIdentities.bearer(importer))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].value").value("import-one.example.org"))
                // 匯入的 IOC 一律租戶私有
                .andExpect(jsonPath("$.items[0].tlp").value("AMBER"));
    }

    /** 本平台匯出的 bundle 必須能再匯入(pattern 模板是 §7.8.3 的固定六種)。 */
    @Test
    void stixBundleImportRoundTripsOurOwnPatterns() throws Exception {
        AuthSession importer = premium("import-stix@example.org");
        String bundle = """
                {"type":"bundle","id":"bundle--0f2d7b3c-9a41-4a7e-8b2f-1c5d6e7f8a90","objects":[
                  {"type":"indicator","spec_version":"2.1",
                   "id":"indicator--1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
                   "pattern":"[domain-name:value = 'stix-import.example.org']","pattern_type":"stix",
                   "valid_from":"2026-08-01T00:00:00Z","labels":["HIGH","imported-stix"],"confidence":75}]}
                """;

        String jobId = accept(importer, MediaType.APPLICATION_JSON_VALUE, bundle);
        JsonNode finished = awaitTerminal(importer, jobId);

        assertThat(finished.get("status").asString()).isEqualTo("SUCCESS");
        assertThat(finished.get("acceptedCount").asInt()).isEqualTo(1);
        mvc.perform(asClient(get("/api/v1/iocs?tags=imported-stix")
                        .header("Authorization", TestIdentities.bearer(importer))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].value").value("stix-import.example.org"))
                .andExpect(jsonPath("$.items[0].severity").value("HIGH"));
    }

    /** 整批無法解碼 → 400,不建立 job(沒有可查的進度就不該給 jobId)。 */
    @Test
    void undecodablePayloadIsRejectedUpFront() throws Exception {
        AuthSession importer = premium("import-broken@example.org");

        mvc.perform(asClient(post("/api/v1/iocs/import")
                        .header("Authorization", TestIdentities.bearer(importer))
                        .contentType("text/csv")
                        .content("type,unknown_column\nDOMAIN,x\n")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    /** TLP:RED 不進入平台(07):整份 bundle 拒收。 */
    @Test
    void bundleWithRedMarkingIsRejected() throws Exception {
        AuthSession importer = premium("import-red@example.org");
        String bundle = """
                {"type":"bundle","id":"bundle--0f2d7b3c-9a41-4a7e-8b2f-1c5d6e7f8a91","objects":[
                  {"type":"indicator","spec_version":"2.1",
                   "id":"indicator--2a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
                   "pattern":"[domain-name:value = 'red.example.org']","pattern_type":"stix",
                   "valid_from":"2026-08-01T00:00:00Z",
                   "object_marking_refs":["marking-definition--e828b379-4e03-4974-9ac4-e53a884c97c1"]}]}
                """;

        mvc.perform(asClient(post("/api/v1/iocs/import")
                        .header("Authorization", TestIdentities.bearer(importer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bundle)))
                .andExpect(status().isBadRequest());
    }

    /** 跨租戶的 job 一律視為不存在(§9.4:404,不回 403)。 */
    @Test
    void anotherTenantsJobIsNotFound() throws Exception {
        AuthSession importer = premium("import-owner@example.org");
        String jobId = accept(importer, "text/csv", "type,value\nDOMAIN,job-scope.example.org\n");
        AuthSession stranger = premium("import-stranger@example.org");

        mvc.perform(asClient(
                        get("/api/v1/iocs/import/" + jobId).header("Authorization", TestIdentities.bearer(stranger))))
                .andExpect(status().isNotFound());
    }

    /** 不支援的 Content-Type → 415(§9.4)。 */
    @Test
    void unsupportedContentTypeIsRejected() throws Exception {
        AuthSession importer = premium("import-media@example.org");

        mvc.perform(asClient(post("/api/v1/iocs/import")
                        .header("Authorization", TestIdentities.bearer(importer))
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("type,value\nDOMAIN,x.example.org\n")))
                .andExpect(status().isUnsupportedMediaType());
    }

    private static MockHttpServletRequestBuilder asClient(MockHttpServletRequestBuilder builder) {
        return builder.with(request -> {
            request.setRemoteAddr(CLIENT_IP);
            return request;
        });
    }
}
