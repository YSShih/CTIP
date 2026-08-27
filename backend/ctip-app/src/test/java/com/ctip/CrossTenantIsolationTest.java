package com.ctip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ctip.application.identity.AuthService;
import com.ctip.application.identity.AuthSession;
import com.ctip.application.port.IndicatorRepository;
import com.ctip.application.port.SourceRepository;
import com.ctip.application.port.TenantMembershipRepository;
import com.ctip.application.rbac.RoleCode;
import com.ctip.domain.indicator.IndicatorId;
import com.ctip.domain.source.SourceId;
import com.ctip.domain.tenant.TenantId;
import com.ctip.sdk.RedistributionPolicy;
import com.ctip.sdk.SourceType;
import com.ctip.sdk.Tlp;
import com.ctip.support.IndicatorFixtures;
import com.ctip.support.TestIdentities;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

/**
 * DoD M2-06:租戶 A 無法存取租戶 B 的任何資源,<strong>每一個</strong> tenant-scoped 端點
 * 一律回 404(非 403;docs/spec/14-testing.md §14.4 條號 3、09 §9.4)。
 * 端點清單以參數化涵蓋,不可只測代表。
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CrossTenantIsolationTest extends AbstractPostgresIntegrationTest {

    private static final String CLIENT_IP = "10.20.0.14";
    private static final IndicatorId OWNED = new IndicatorId(UUID.fromString("00000000-0000-0000-0000-0000000000c1"));

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private AuthService authService;

    @Autowired
    private TenantMembershipRepository memberships;

    @Autowired
    private IndicatorRepository indicators;

    @Autowired
    private SourceRepository sources;

    private AuthSession owner;
    private AuthSession intruder;
    private String ownedApiKeyId;

    @BeforeAll
    void registerTenants() {
        TestIdentities identities = new TestIdentities(authService, memberships);
        owner = identities.register("xtenant-owner@example.org", RoleCode.TENANT_ADMIN);
        intruder = identities.register("xtenant-intruder@example.org", RoleCode.TENANT_ADMIN);
        seedOwnedIndicator(owner.identity().tenantId());
    }

    /** 每個測試方法都重新建立一把 owner 的 API key(其中一個測試會把它撤銷)。 */
    @BeforeEach
    void issueOwnedApiKey() throws Exception {
        ownedApiKeyId = createOwnedApiKey();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "GET /api/v1/iocs/{indicatorId}",
                "GET /api/v1/iocs/{indicatorId}/sources",
                "GET /api/v1/stix/indicator--{indicatorId}",
                "DELETE /api/v1/api-keys/{apiKeyId}"
            })
    void everyTenantScopedEndpointReturnsNotFoundForOtherTenants(String endpoint) throws Exception {
        String[] parts = endpoint.split(" ", 2);
        String url = parts[1].replace("{indicatorId}", OWNED.value().toString()).replace("{apiKeyId}", ownedApiKeyId);
        MockHttpServletRequestBuilder request = "DELETE".equals(parts[0]) ? delete(url) : get(url);
        mvc.perform(asClient(request).header("Authorization", TestIdentities.bearer(intruder)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    /** 對照組:擁有租戶自己看得到,證明 404 來自隔離而非資料不存在。 */
    @Test
    void ownerCanReachTheSameResources() throws Exception {
        String bearer = TestIdentities.bearer(owner);
        mvc.perform(asClient(get("/api/v1/iocs/" + OWNED.value())).header("Authorization", bearer))
                .andExpect(status().isOk());
        mvc.perform(asClient(get("/api/v1/iocs/" + OWNED.value() + "/sources")).header("Authorization", bearer))
                .andExpect(status().isOk());
        mvc.perform(asClient(delete("/api/v1/api-keys/" + ownedApiKeyId)).header("Authorization", bearer))
                .andExpect(status().isNoContent());
    }

    /** 列表端點不得洩漏他租戶資料(隔離的另一面:不是 404 而是不出現)。 */
    @Test
    void listEndpointsExcludeOtherTenantsData() throws Exception {
        String body = mvc.perform(asClient(get("/api/v1/iocs?limit=200"))
                        .header("Authorization", TestIdentities.bearer(intruder)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(body).doesNotContain(OWNED.value().toString());
    }

    private String createOwnedApiKey() throws Exception {
        String response = mvc.perform(asClient(post("/api/v1/api-keys")
                        .header("Authorization", TestIdentities.bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"owned-key\",\"scopes\":[\"ioc:read\"]}")))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return json.readTree(response).at("/apiKey/id").asString();
    }

    private void seedOwnedIndicator(TenantId ownerTenant) {
        SourceId sourceId = sources.findBySourceType(SourceType.MOCK_OPENPHISH)
                .orElseThrow()
                .id();
        IndicatorFixtures.upsert(
                indicators,
                sourceId,
                new IndicatorFixtures.Fixture(
                        OWNED, ownerTenant, Tlp.AMBER, RedistributionPolicy.ATTRIBUTION_REQUIRED, "xtenant-owned"));
    }

    private static MockHttpServletRequestBuilder asClient(MockHttpServletRequestBuilder builder) {
        return builder.with(request -> {
            request.setRemoteAddr(CLIENT_IP);
            return request;
        });
    }
}
