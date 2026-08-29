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
import com.ctip.domain.plan.Plan;
import com.ctip.domain.plan.PlanCode;
import com.ctip.support.TestIdentities;
import com.ctip.support.TestPlans;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

/**
 * 限流維度 1–3 與 5(docs/spec/10-identity-plans.md §10.7,Phase 17)。
 * 維度 4(匿名 IP)與 429 的回應形狀在 {@link RateLimitTest};
 * 多實例的共用配額在 {@link DistributedRateLimitTest}。
 *
 * <p>每個測試方法用自己的 client IP:限流狀態在記憶體中跨測試類共用。
 */
@AutoConfigureMockMvc
class IdentityRateLimitTest extends AbstractPostgresIntegrationTest {

    /** 打滿 3 次就超限;真實值(FREE 300/min)打起來太慢。 */
    private static final int PER_MINUTE = 3;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private PlanRepository plans;

    @Autowired
    private SubscriptionRepository subscriptions;

    @Autowired
    private IdGeneratorPort idGenerator;

    @Autowired
    private ClockPort clock;

    @Autowired
    private AuthService authService;

    @Autowired
    private TenantMembershipRepository memberships;

    private TestIdentities identities;
    private TestPlans planAdmin;
    private Plan originalAnonymous;

    @BeforeEach
    void setUp() {
        identities = new TestIdentities(authService, memberships);
        planAdmin = new TestPlans(plans, subscriptions, idGenerator, clock);
        originalAnonymous = planAdmin.plan(PlanCode.ANONYMOUS);
    }

    @AfterEach
    void restoreAnonymousQuota() {
        planAdmin.save(originalAnonymous);
    }

    /**
     * <strong>已認證的呼叫者不受匿名配額約束</strong>(ADR 0026)。
     *
     * <p>維度 4 排在認證之前,因此每個請求都會先扣一枚匿名 token;認證成功後必須歸還,
     * 否則 ENTERPRISE 的 client 會被 60/min 綁死——把匿名配額壓到 1 就能量出這件事:
     * 不歸還的話第二個請求就會 429。
     */
    @Test
    void authenticatedCallerIsNotCappedByTheAnonymousIpQuota() throws Exception {
        planAdmin.save(TestPlans.requestsPerMinute(1).apply(originalAnonymous));
        AuthSession session = identities.register("ratelimit-refund@example.org", RoleCode.TENANT_ADMIN);

        for (int i = 0; i < 5; i++) {
            mvc.perform(from(
                            get("/api/v1/iocs?limit=1").header("Authorization", TestIdentities.bearer(session)),
                            "10.40.0.1"))
                    .andExpect(status().isOk());
        }
        // 對照組:同一個 IP 的匿名請求仍然只有 1 次
        mvc.perform(from(get("/api/v1/iocs?limit=1"), "10.40.0.1")).andExpect(status().isOk());
        mvc.perform(from(get("/api/v1/iocs?limit=1"), "10.40.0.1")).andExpect(status().isTooManyRequests());
    }

    /** 維度 2／3:已認證請求改受自己方案的限額約束,標頭反映的也是那個數字。 */
    @Test
    void authenticatedCallerIsLimitedByItsOwnPlan() throws Exception {
        AuthSession session = identities.register("ratelimit-plan@example.org", RoleCode.TENANT_ADMIN);
        planAdmin.assign(session.identity().tenantId(), PlanCode.PREMIUM);

        planAdmin.withPlan(PlanCode.PREMIUM, TestPlans.requestsPerMinute(PER_MINUTE), () -> {
            for (int i = 0; i < PER_MINUTE; i++) {
                mvc.perform(from(
                                get("/api/v1/iocs?limit=1").header("Authorization", TestIdentities.bearer(session)),
                                "10.40.0.2"))
                        .andExpect(status().isOk())
                        .andExpect(header().string("X-RateLimit-Limit", String.valueOf(PER_MINUTE)))
                        .andExpect(header().string("X-RateLimit-Remaining", String.valueOf(PER_MINUTE - 1 - i)));
            }
            mvc.perform(from(
                            get("/api/v1/iocs?limit=1").header("Authorization", TestIdentities.bearer(session)),
                            "10.40.0.2"))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"))
                    .andExpect(header().exists("Retry-After"));
        });
    }

    /**
     * 維度 1:API key 是最 specific 的維度,標頭因此以它為準。
     * 同一個租戶的兩把 key 各有自己的桶——否則「為 CI 另開一把 key」不會改變任何事。
     */
    @Test
    void eachApiKeyHasItsOwnBucket() throws Exception {
        AuthSession owner = identities.register("ratelimit-key@example.org", RoleCode.TENANT_ADMIN);
        planAdmin.assign(owner.identity().tenantId(), PlanCode.PREMIUM);
        String first = createKey(owner, "first");
        String second = createKey(owner, "second");

        planAdmin.withPlan(PlanCode.PREMIUM, TestPlans.requestsPerMinute(PER_MINUTE), () -> {
            for (int i = 0; i < PER_MINUTE; i++) {
                mvc.perform(from(get("/api/v1/iocs?limit=1").header("X-API-Key", first), "10.40.0.3"))
                        .andExpect(status().isOk());
            }
            mvc.perform(from(get("/api/v1/iocs?limit=1").header("X-API-Key", first), "10.40.0.3"))
                    .andExpect(status().isTooManyRequests());
            // 第二把 key 的桶是空的;但同一個租戶的維度 3 已被第一把用掉,故這次仍會被拒——
            // 這正是「由 specific 到 general 依序檢查,任一超限即拒絕」的可觀察後果
            mvc.perform(from(get("/api/v1/iocs?limit=1").header("X-API-Key", second), "10.40.0.3"))
                    .andExpect(status().isTooManyRequests());
        });
    }

    /**
     * 維度 5:heavy 類別只有總配額的 5%(ADR 0020),因此 heavy 端點的標頭
     * 必須顯示比總配額小的數字——而 read 端點顯示的是總配額。
     */
    @Test
    void heavyEndpointsGetASmallerShareThanReads() throws Exception {
        AuthSession session = identities.register("ratelimit-heavy@example.org", RoleCode.TENANT_ADMIN);
        planAdmin.assign(session.identity().tenantId(), PlanCode.PREMIUM);

        planAdmin.withPlan(PlanCode.PREMIUM, TestPlans.requestsPerMinute(100), () -> {
            mvc.perform(from(
                            get("/api/v1/iocs?limit=1").header("Authorization", TestIdentities.bearer(session)),
                            "10.40.0.4"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-RateLimit-Limit", "100"));
            // heavy:100 的 5% = 5,且它比 read 緊,標頭必須反映最緊的維度
            mvc.perform(from(
                            get("/api/v1/stix/bundle").header("Authorization", TestIdentities.bearer(session)),
                            "10.40.0.4"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-RateLimit-Limit", "5"));
        });
    }

    private String createKey(AuthSession owner, String name) throws Exception {
        String response = mvc.perform(from(
                        post("/api/v1/api-keys")
                                .header("Authorization", TestIdentities.bearer(owner))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"" + name + "\",\"scopes\":[\"ioc:read\"]}"),
                        "10.40.0.3"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return json.readTree(response).get("key").asString();
    }

    private static MockHttpServletRequestBuilder from(MockHttpServletRequestBuilder builder, String ip) {
        return builder.with(request -> {
            request.setRemoteAddr(ip);
            return request;
        });
    }
}
