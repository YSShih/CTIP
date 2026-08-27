package com.ctip;

import static com.ctip.support.SecurityFixtures.B_AMBER;
import static com.ctip.support.SecurityFixtures.DEMO;
import static com.ctip.support.SecurityFixtures.DEMO_INTERNAL_ONLY;
import static com.ctip.support.SecurityFixtures.PUBLIC_CLEAR;
import static com.ctip.support.SecurityFixtures.PUBLIC_CLEAR_INTERNAL;
import static com.ctip.support.SecurityFixtures.PUBLIC_GREEN;
import static com.ctip.support.SecurityFixtures.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ctip.application.identity.ApiKeyIssueRequest;
import com.ctip.application.identity.ApiKeyService;
import com.ctip.application.identity.AuthCommands;
import com.ctip.application.identity.AuthService;
import com.ctip.application.identity.AuthSession;
import com.ctip.application.identity.AuthenticatedIdentity;
import com.ctip.application.identity.InvalidRefreshTokenException;
import com.ctip.application.indicator.IndicatorFilter;
import com.ctip.application.port.AccessTokenClaims;
import com.ctip.application.port.IndicatorRepository;
import com.ctip.application.port.SourceRepository;
import com.ctip.application.port.TenantMembershipRepository;
import com.ctip.application.port.TenantRepository;
import com.ctip.application.rbac.RoleCode;
import com.ctip.config.CtipProperties;
import com.ctip.domain.identity.IssuedApiKey;
import com.ctip.domain.identity.ScopeSet;
import com.ctip.domain.indicator.Indicator;
import com.ctip.domain.shared.Visibility;
import com.ctip.domain.tenant.TenantId;
import com.ctip.infrastructure.security.JwtAccessTokenAdapter;
import com.ctip.sdk.Tlp;
import com.ctip.support.LogCapture;
import com.ctip.support.SecurityFixtures;
import com.ctip.support.TestIdentities;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 安全測試條號 1–9(docs/spec/14-testing.md §14.4;DoD M2-07)。1、2、3、9 於 repository 層與 REST
 * 端點層雙重驗證;4、5、6 為認證與 API key;7 為限流;8 檢查日誌不含 secret。方法名含條號以便追溯。
 */
@AutoConfigureMockMvc
class SecurityTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private IndicatorRepository indicators;

    @Autowired
    private TenantRepository tenants;

    @Autowired
    private SourceRepository sources;

    @Autowired
    private AuthService authService;

    @Autowired
    private ApiKeyService apiKeys;

    @Autowired
    private TenantMembershipRepository memberships;

    @Autowired
    private CtipProperties properties;

    @BeforeEach
    void seedSecurityFixtures() {
        SecurityFixtures.seed(tenants, sources, indicators);
    }

    /**
     * ADR 0006 回歸鎖(query 層):public 租戶不得因 viewer == owner 而豁免再散布過濾。
     * 此 fixture(public + CLEAR + 全來源 INTERNAL_ONLY)是唯一能判別
     * TlpSpecifications.ownerOrRedistributable 的樣本——tenant scope 與 TLP 條件都擋不住它;
     * 若豁免條件退化回「viewer == owner 即豁免」,本測試立即失敗。
     */
    @Test
    void security9_publicInternalOnlyIndicatorIsInvisibleToAnonymous() throws Exception {
        Visibility anonymous = Visibility.anonymous();
        assertThat(indicators.findVisibleById(PUBLIC_CLEAR_INTERNAL, anonymous)).isEmpty();
        assertThat(page(anonymous)).noneMatch(i -> i.id().equals(PUBLIC_CLEAR_INTERNAL));
        // 端點層:404,不洩漏存在性
        mvc.perform(asTestIp(get("/api/v1/iocs/" + PUBLIC_CLEAR_INTERNAL.value())))
                .andExpect(status().isNotFound());
    }

    @Test
    void security1_anonymousSeesOnlyPublicClear() {
        Visibility anonymous = Visibility.anonymous();
        assertThat(indicators.findVisibleById(PUBLIC_CLEAR, anonymous)).isPresent();
        assertThat(indicators.findVisibleById(PUBLIC_GREEN, anonymous)).isEmpty();
        assertThat(indicators.findVisibleById(B_AMBER, anonymous)).isEmpty();

        assertThat(page(anonymous)).isNotEmpty().allSatisfy(i -> {
            assertThat(i.ownerTenantId()).isEqualTo(TenantId.PUBLIC);
            assertThat(i.tlp()).isEqualTo(Tlp.CLEAR);
        });
    }

    @Test
    void security2_authenticatedSeesPublicClearGreenButNotOtherTenantsAmber() {
        Visibility demoView = Visibility.authenticated(DEMO);
        assertThat(indicators.findVisibleById(PUBLIC_CLEAR, demoView)).isPresent();
        assertThat(indicators.findVisibleById(PUBLIC_GREEN, demoView)).isPresent();
        assertThat(indicators.findVisibleById(B_AMBER, demoView)).isEmpty();

        assertThat(page(demoView))
                .allSatisfy(i -> assertThat(i.ownerTenantId().equals(DEMO)
                                || i.ownerTenantId().isPublic())
                        .isTrue());
    }

    @Test
    void security3_crossTenantAccessYieldsNotFound() {
        Visibility tenantBView = Visibility.authenticated(TENANT_B);
        assertThat(indicators.findVisibleById(DEMO_INTERNAL_ONLY, tenantBView)).isEmpty();
        assertThat(page(tenantBView)).noneMatch(i -> i.ownerTenantId().equals(DEMO));
        // 擁有租戶自己查得到(對照組)
        assertThat(indicators.findVisibleById(B_AMBER, tenantBView)).isPresent();
    }

    @Test
    void security9_internalOnlyDataIsInvisibleToNonOwnersButFullyVisibleToOwner() {
        Visibility owner = Visibility.authenticated(DEMO);
        assertThat(indicators.findVisibleById(DEMO_INTERNAL_ONLY, owner)).isPresent();

        assertThat(indicators.findVisibleById(DEMO_INTERNAL_ONLY, Visibility.authenticated(TENANT_B)))
                .isEmpty();
        assertThat(indicators.findVisibleById(DEMO_INTERNAL_ONLY, Visibility.anonymous()))
                .isEmpty();

        Indicator internalOnly = indicators.findById(DEMO_INTERNAL_ONLY).orElseThrow();
        assertThat(internalOnly.canBeRedistributedTo(TENANT_B)).isFalse();
        assertThat(internalOnly.eligibleForBloom()).isFalse();
    }

    /** 條號 1(端點層):匿名經 HTTP 只拿得到 public CLEAR;GREEN 一律 404。 */
    @Test
    void security1_anonymousEndpointServesOnlyPublicClear() throws Exception {
        mvc.perform(asTestIp(get("/api/v1/iocs/" + PUBLIC_CLEAR.value()))).andExpect(status().isOk());
        mvc.perform(asTestIp(get("/api/v1/iocs/" + PUBLIC_GREEN.value()))).andExpect(status().isNotFound());
    }

    /**
     * 條號 3(端點層):跨租戶資源於 M1 全部 tenant-scoped 讀取端點一律 404,不得 403
     * (docs/spec/14-testing.md §14.4:以參數化涵蓋端點清單,不可只測代表)。
     */
    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/iocs/{id}", "/api/v1/iocs/{id}/sources", "/api/v1/stix/indicator--{id}"})
    void security3_crossTenantEndpointsReturn404NotForbidden(String template) throws Exception {
        String url = template.replace("{id}", B_AMBER.value().toString());
        mvc.perform(asTestIp(get(url))).andExpect(status().isNotFound());
    }

    /** 條號 9(端點層):INTERNAL_ONLY(demo 擁有)對匿名於任何端點一律 404。 */
    @Test
    void security9_internalOnlyIsNotFoundOverEndpoints() throws Exception {
        mvc.perform(asTestIp(get("/api/v1/iocs/" + DEMO_INTERNAL_ONLY.value()))).andExpect(status().isNotFound());
        mvc.perform(asTestIp(get("/api/v1/iocs/" + DEMO_INTERNAL_ONLY.value() + "/sources")))
                .andExpect(status().isNotFound());
    }

    private List<Indicator> page(Visibility visibility) {
        return indicators
                .findVisible(visibility, IndicatorFilter.none(), null, 5000)
                .items();
    }

    /** 端點層測試用獨立 client IP,避免與條號 7 共用匿名限流 bucket。 */
    private static MockHttpServletRequestBuilder asTestIp(MockHttpServletRequestBuilder builder) {
        return builder.with(req -> {
            req.setRemoteAddr("203.0.113.104");
            return req;
        });
    }

    /** 條號 7:超出限流回 429,且帶 X-RateLimit-* 與 Retry-After(mvp 預設匿名 60/min)。 */
    @Test
    void security7AnonymousExceedingRateLimitGets429WithHeaders() throws Exception {
        for (int i = 0; i < 60; i++) {
            mvc.perform(get("/api/v1/security7-probe")).andExpect(header().exists("X-RateLimit-Limit"));
        }
        mvc.perform(get("/api/v1/security7-probe"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("X-RateLimit-Remaining", "0"))
                .andExpect(header().exists("Retry-After"));
    }

    /**
     * 條號 4:過期 token 被拒,且與「無效 token」可區分(§9.4:TOKEN_EXPIRED vs UNAUTHENTICATED)。
     * 以相同 secret、過去時鐘簽出一枚必然過期的 token。
     */
    @Test
    void security4ExpiredAccessTokenIsRejected() throws Exception {
        AuthSession session = identities().register("sec4@example.org", RoleCode.USER);
        AuthenticatedIdentity identity = session.identity();
        String expired = new JwtAccessTokenAdapter(
                        properties.jwt().secret(),
                        Duration.ofSeconds(60),
                        () -> Instant.now().minusSeconds(3600))
                .issue(new AccessTokenClaims(
                        identity.userId(),
                        identity.tenantId(),
                        Set.of(identity.role().name()),
                        identity.permissions(),
                        UUID.randomUUID()));

        mvc.perform(asTestIp(get("/api/v1/api-keys")).header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
        mvc.perform(asTestIp(get("/api/v1/api-keys")).header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(asTestIp(get("/api/v1/api-keys")).header("Authorization", TestIdentities.bearer(session)))
                .andExpect(status().isOk());
    }

    /** 條號 5:撤銷的 refresh token 被拒,且重用觸發該 family 全面撤銷(不變量 U4–U6)。 */
    @Test
    void security5RefreshTokenReuseRevokesTheWholeFamily() {
        AuthSession first = identities().register("sec5@example.org", RoleCode.USER);
        AuthSession second = refresh(first.refreshToken());

        assertThatThrownBy(() -> refresh(first.refreshToken())).isInstanceOf(InvalidRefreshTokenException.class);
        // family 全撤:當時仍有效的最新一枚也失效
        assertThatThrownBy(() -> refresh(second.refreshToken())).isInstanceOf(InvalidRefreshTokenException.class);
    }

    /** 條號 6:撤銷的 API key 被拒;API key 的 scope 無法超出建立者權限(不變量 K4)。 */
    @Test
    void security6RevokedApiKeyIsRejectedAndScopeCannotEscalate() throws Exception {
        AuthSession user = identities().register("sec6@example.org", RoleCode.USER);
        assertThatThrownBy(() -> issueKey(user, "escalation", "ioc:submit"))
                .isInstanceOf(IllegalArgumentException.class);

        IssuedApiKey issued = issueKey(user, "readonly", "stix:export");
        mvc.perform(asTestIp(get("/api/v1/stix/bundle")).header("X-API-Key", issued.plaintext()))
                .andExpect(status().isOk());

        apiKeys.revoke(issued.apiKey().id(), user.identity().tenantId());
        mvc.perform(asTestIp(get("/api/v1/stix/bundle")).header("X-API-Key", issued.plaintext()))
                .andExpect(status().isUnauthorized());
    }

    /** 條號 8:日誌不得出現密碼、JWT secret、API key 原文、refresh token 原文、access token。 */
    @Test
    void security8SecretsNeverReachTheLogs() throws Exception {
        try (LogCapture logs = LogCapture.start()) {
            AuthSession session = identities().register("sec8@example.org", RoleCode.TENANT_ADMIN);
            IssuedApiKey issued = issueKey(session, "log-probe", "ioc:read");
            mvc.perform(asTestIp(get("/api/v1/iocs?limit=1")).header("X-API-Key", issued.plaintext()))
                    .andExpect(status().isOk());
            mvc.perform(asTestIp(get("/api/v1/api-keys")).header("Authorization", TestIdentities.bearer(session)))
                    .andExpect(status().isOk());

            assertThat(logs.text())
                    .doesNotContain(TestIdentities.PASSWORD)
                    .doesNotContain(properties.jwt().secret())
                    .doesNotContain(issued.plaintext())
                    .doesNotContain(session.refreshToken())
                    .doesNotContain(session.accessToken());
        }
    }

    private AuthSession refresh(String refreshToken) {
        return authService.refresh(new AuthCommands.Refresh(refreshToken, "junit", "127.0.0.1"));
    }

    private IssuedApiKey issueKey(AuthSession session, String name, String scope) {
        AuthenticatedIdentity identity = session.identity();
        return apiKeys.issue(
                new ApiKeyIssueRequest(identity.tenantId(), identity.userId(), name, new ScopeSet(Set.of(scope)), null),
                identity);
    }

    private TestIdentities identities() {
        return new TestIdentities(authService, memberships);
    }
}
