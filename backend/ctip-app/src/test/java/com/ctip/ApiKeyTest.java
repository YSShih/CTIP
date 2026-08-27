package com.ctip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ctip.application.identity.AuthService;
import com.ctip.application.identity.AuthSession;
import com.ctip.application.port.TenantMembershipRepository;
import com.ctip.application.rbac.RoleCode;
import com.ctip.support.TestIdentities;
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
 * DoD M2-05:API key 建立(原文僅回傳一次)、撤銷、scope 檢查、不可提權
 * (docs/spec/10-identity-plans.md §10.5;不變量 K1–K7 的端點層驗證)。
 */
@AutoConfigureMockMvc
class ApiKeyTest extends AbstractPostgresIntegrationTest {

    private static final String CLIENT_IP = "10.20.0.13";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private AuthService authService;

    @Autowired
    private TenantMembershipRepository memberships;

    private TestIdentities identities;

    @BeforeEach
    void setUp() {
        identities = new TestIdentities(authService, memberships);
    }

    private JsonNode createKey(AuthSession owner, String name, String scopesJson, int expectedStatus) throws Exception {
        String response = mvc.perform(asClient(post("/api/v1/api-keys")
                        .header("Authorization", TestIdentities.bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"scopes\":" + scopesJson + "}")))
                .andExpect(status().is(expectedStatus))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return json.readTree(response);
    }

    @Test
    void keyIsReturnedOnceAndNeverAgain() throws Exception {
        AuthSession owner = identities.register("apikey-once@example.org", RoleCode.TENANT_ADMIN);
        JsonNode issued = createKey(owner, "ci-pipeline", "[\"ioc:read\"]", 201);

        String fullKey = issued.get("key").asString();
        assertThat(fullKey).matches("^ctip_(mvp|dev|stg|prod)_[0-9A-Za-z]{32}$");
        String prefix = issued.at("/apiKey/keyPrefix").asString();
        assertThat(fullKey).contains(prefix);
        assertThat(prefix).isNotEqualTo("ctip_mvp");

        String listed = mvc.perform(
                        asClient(get("/api/v1/api-keys").header("Authorization", TestIdentities.bearer(owner))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(listed).contains(prefix).doesNotContain(fullKey);
    }

    @Test
    void issuedKeyAuthenticatesSubsequentRequests() throws Exception {
        AuthSession owner = identities.register("apikey-auth@example.org", RoleCode.TENANT_ADMIN);
        String fullKey = createKey(owner, "reader", "[\"ioc:read\",\"stix:export\"]", 201)
                .get("key")
                .asString();

        mvc.perform(asClient(get("/api/v1/stix/bundle").header("X-API-Key", fullKey)))
                .andExpect(status().isOk());
        // scope 之外的權限不因持有者角色而生效(有效權限 = scopes ∩ 角色權限)
        mvc.perform(asClient(get("/api/v1/api-keys").header("X-API-Key", fullKey)))
                .andExpect(status().isForbidden());
    }

    @Test
    void revokedKeyIsRejected() throws Exception {
        AuthSession owner = identities.register("apikey-revoke@example.org", RoleCode.TENANT_ADMIN);
        JsonNode issued = createKey(owner, "short-lived", "[\"stix:export\"]", 201);
        String fullKey = issued.get("key").asString();
        String id = issued.at("/apiKey/id").asString();

        mvc.perform(asClient(get("/api/v1/stix/bundle").header("X-API-Key", fullKey)))
                .andExpect(status().isOk());

        mvc.perform(asClient(delete("/api/v1/api-keys/" + id).header("Authorization", TestIdentities.bearer(owner))))
                .andExpect(status().isNoContent());

        mvc.perform(asClient(get("/api/v1/stix/bundle").header("X-API-Key", fullKey)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    /** 不變量 K4:scope 不得超出建立者權限——USER 沒有 ioc:submit。 */
    @Test
    void scopeMayNotExceedCreatorPermissions() throws Exception {
        AuthSession user = identities.register("apikey-escalate@example.org", RoleCode.USER);
        createKey(user, "escalation-attempt", "[\"ioc:submit\"]", 400);
        createKey(user, "within-permissions", "[\"ioc:read\",\"stix:export\"]", 201);
    }

    /** 跨租戶撤銷:回 404 而非 403(§9.4 不洩漏存在性)。 */
    @Test
    void revokingAnotherTenantsKeyIsNotFound() throws Exception {
        AuthSession owner = identities.register("apikey-owner@example.org", RoleCode.TENANT_ADMIN);
        AuthSession other = identities.register("apikey-other@example.org", RoleCode.TENANT_ADMIN);
        String id = createKey(owner, "owned", "[\"ioc:read\"]", 201)
                .at("/apiKey/id")
                .asString();

        mvc.perform(asClient(delete("/api/v1/api-keys/" + id).header("Authorization", TestIdentities.bearer(other))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        // 其他租戶的清單也看不到這把 key
        mvc.perform(asClient(get("/api/v1/api-keys").header("Authorization", TestIdentities.bearer(other))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void anonymousMayNotCreateOrListKeys() throws Exception {
        mvc.perform(asClient(get("/api/v1/api-keys"))).andExpect(status().isForbidden());
        mvc.perform(asClient(post("/api/v1/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"scopes\":[\"ioc:read\"]}")))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidApiKeyHeaderIsRejected() throws Exception {
        mvc.perform(asClient(get("/api/v1/iocs?limit=1").header("X-API-Key", "ctip_mvp_" + "z".repeat(32))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    private static MockHttpServletRequestBuilder asClient(MockHttpServletRequestBuilder builder) {
        return builder.with(request -> {
            request.setRemoteAddr(CLIENT_IP);
            return request;
        });
    }
}
