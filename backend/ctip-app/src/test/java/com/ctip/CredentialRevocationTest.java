package com.ctip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ctip.application.identity.AuthService;
import com.ctip.application.identity.AuthSession;
import com.ctip.application.port.ApiKeyRepository;
import com.ctip.application.port.ClockPort;
import com.ctip.application.port.TenantMembershipRepository;
import com.ctip.application.port.UserRepository;
import com.ctip.application.rbac.RoleCode;
import com.ctip.domain.identity.ApiKey;
import com.ctip.domain.identity.ApiKeyId;
import com.ctip.domain.user.User;
import com.ctip.support.TestIdentities;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

/**
 * 憑證的撤銷必須真的生效(ADR 0013)。Phase 13 收尾稽核發現三條路徑不一致:
 * 登入會擋 {@code UserStatus != ACTIVE},但 refresh 輪替與 API key 驗證完全不看使用者狀態。
 *
 * <p>停權在 M3 的使用者管理端點出現之前是唯一的事故處置手段——不生效等於沒有。
 * 另含 §10.5 的每租戶數量上限,以及「更新 last_used_at 不得沖掉撤銷」。
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "ctip.api-key.max-per-tenant=2")
class CredentialRevocationTest extends AbstractPostgresIntegrationTest {

    private static final String CLIENT_IP = "10.20.0.21";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private AuthService authService;

    @Autowired
    private TenantMembershipRepository memberships;

    @Autowired
    private UserRepository users;

    @Autowired
    private ApiKeyRepository apiKeys;

    @Autowired
    private ClockPort clock;

    private TestIdentities identities;

    @BeforeEach
    void setUp() {
        identities = new TestIdentities(authService, memberships);
    }

    /** 停權後既有 refresh token 不得再續期——否則被停權的帳號可每 30 天輪替一次無限期存取。 */
    @Test
    void suspendedUserCannotRotateItsRefreshToken() throws Exception {
        AuthSession session = register("suspend-refresh");
        refresh(session.refreshToken(), 200);

        suspend(session);

        mvc.perform(asClient(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(session.refreshToken()))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    /** 停權後既有 API key 不得再通行——金鑰自己沒過期,但持有者已經不該有存取權。 */
    @Test
    void suspendedUserApiKeyStopsWorking() throws Exception {
        AuthSession session = register("suspend-apikey");
        String key = issueKey(session, "still-valid");
        mvc.perform(asClient(get("/api/v1/iocs?limit=1").header("X-API-Key", key)))
                .andExpect(status().isOk());

        suspend(session);

        mvc.perform(asClient(get("/api/v1/iocs?limit=1").header("X-API-Key", key)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    /** 停權後也不得重新登入(登入路徑原本就有擋,這裡鎖住它不被改壞)。 */
    @Test
    void suspendedUserCannotLogInAgain() throws Exception {
        AuthSession session = register("suspend-login");
        suspend(session);

        mvc.perform(asClient(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("suspend-login@example.org", TestIdentities.PASSWORD))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    /** §10.5:每租戶數量上限。原本完全沒有檢查,{@code countActive} 是無呼叫端的死程式。 */
    @Test
    void apiKeyQuotaIsEnforced() throws Exception {
        AuthSession session = register("apikey-quota");
        issueKey(session, "first");
        issueKey(session, "second");

        mvc.perform(asClient(post("/api/v1/api-keys")
                        .header("Authorization", TestIdentities.bearer(session))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"third\",\"scopes\":[\"ioc:read\"]}")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PLAN_LIMIT_EXCEEDED"));
    }

    /**
     * 更新 {@code last_used_at} 不得沖掉撤銷。
     *
     * <p>舊實作的 {@code touch} 走整列覆寫的 {@code save},手上的快照是認證那一刻讀的,
     * 期間若另一個請求撤銷了金鑰,回寫會把 {@code revoked_at} 覆寫回 null。
     * 這與 M1 複查抓到的 {@code IndicatorSource.mergeReport} 沖掉撤回是同一類缺陷。
     */
    @Test
    void markingKeyAsUsedNeverResurrectsARevokedKey() throws Exception {
        AuthSession session = register("apikey-touch");
        String plaintext = issueKey(session, "touched");
        ApiKeyId id = new ApiKeyId(UUID.fromString(
                json.readTree(listKeys(session)).get(0).get("id").asString()));

        mvc.perform(asClient(get("/api/v1/iocs?limit=1").header("X-API-Key", plaintext)))
                .andExpect(status().isOk());
        mvc.perform(asClient(delete(id, session))).andExpect(status().isNoContent());

        apiKeys.markUsed(id, clock.now());

        assertThat(apiKeys.findById(id).map(ApiKey::revokedAt))
                .isPresent()
                .get()
                .isNotNull();
        mvc.perform(asClient(get("/api/v1/iocs?limit=1").header("X-API-Key", plaintext)))
                .andExpect(status().isUnauthorized());
    }

    private AuthSession register(String localPart) {
        return identities.register(localPart + "@example.org", RoleCode.TENANT_ADMIN);
    }

    private void suspend(AuthSession session) {
        User user = users.findById(session.identity().userId()).orElseThrow();
        user.suspend();
        users.save(user);
    }

    private void refresh(String refreshToken, int expected) throws Exception {
        mvc.perform(asClient(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(refreshToken))))
                .andExpect(status().is(expected));
    }

    private String issueKey(AuthSession owner, String name) throws Exception {
        String body = mvc.perform(asClient(post("/api/v1/api-keys")
                        .header("Authorization", TestIdentities.bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"scopes\":[\"ioc:read\"]}")))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return json.readTree(body).get("key").asString();
    }

    private String listKeys(AuthSession owner) throws Exception {
        return mvc.perform(asClient(get("/api/v1/api-keys").header("Authorization", TestIdentities.bearer(owner))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private static MockHttpServletRequestBuilder delete(ApiKeyId id, AuthSession owner) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                        "/api/v1/api-keys/{id}", id.value())
                .header("Authorization", TestIdentities.bearer(owner));
    }

    private static String refreshBody(String refreshToken) {
        return "{\"refreshToken\":\"" + refreshToken + "\"}";
    }

    private static String loginBody(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    /** 獨立 client IP,避免與其他測試類共用匿名限流 bucket。 */
    private static MockHttpServletRequestBuilder asClient(MockHttpServletRequestBuilder builder) {
        return builder.with(request -> {
            request.setRemoteAddr(CLIENT_IP);
            return request;
        });
    }
}
