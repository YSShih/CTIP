package com.ctip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ctip.application.indicator.IndicatorFilter;
import com.ctip.application.port.IndicatorRepository;
import com.ctip.application.port.SourceRepository;
import com.ctip.application.port.TenantRepository;
import com.ctip.domain.fingerprint.Sha256FingerprintStrategy;
import com.ctip.domain.indicator.Indicator;
import com.ctip.domain.indicator.IndicatorId;
import com.ctip.domain.indicator.IndicatorSourceSnapshot;
import com.ctip.domain.indicator.IocValue;
import com.ctip.domain.indicator.NewIndicatorCommand;
import com.ctip.domain.indicator.SourceRecordStatus;
import com.ctip.domain.shared.Visibility;
import com.ctip.domain.source.Reputation;
import com.ctip.domain.source.SourceId;
import com.ctip.domain.tenant.Tenant;
import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.tenant.TenantSlug;
import com.ctip.domain.tenant.TenantType;
import com.ctip.sdk.Confidence;
import com.ctip.sdk.IocType;
import com.ctip.sdk.RedistributionPolicy;
import com.ctip.sdk.Severity;
import com.ctip.sdk.SourceType;
import com.ctip.sdk.Tlp;
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

/**
 * 安全測試 1、2、3、7、9(docs/spec/14-testing.md §14.4):1、2、3、9 於 repository/Specification 層
 * 驗證(Phase 4);7(限流 429)於 filter 層驗證(Phase 6)。Phase 9 起同一批條號在 REST 端點層
 * (404 語意)再驗一次。方法名含條號以便追溯。
 */
@AutoConfigureMockMvc
class SecurityTest extends AbstractPostgresIntegrationTest {

    private static final Instant SEEN = Instant.parse("2026-08-10T00:00:00Z");
    private static final TenantId DEMO = new TenantId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    private static final TenantId TENANT_B = new TenantId(UUID.fromString("00000000-0000-0000-0000-00000000000b"));

    private SourceId mockSourceId;

    private static final IndicatorId PUBLIC_GREEN = fixedId("41");
    private static final IndicatorId PUBLIC_CLEAR = fixedId("42");
    private static final IndicatorId B_AMBER = fixedId("43");
    private static final IndicatorId DEMO_INTERNAL_ONLY = fixedId("44");
    private static final IndicatorId PUBLIC_CLEAR_INTERNAL = fixedId("45");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private IndicatorRepository indicators;

    @Autowired
    private TenantRepository tenants;

    @Autowired
    private SourceRepository sources;

    @BeforeEach
    void seedSecurityFixtures() {
        mockSourceId = sources.findBySourceType(SourceType.MOCK_OPENPHISH)
                .orElseThrow()
                .id();
        if (tenants.findById(TENANT_B).isEmpty()) {
            tenants.save(Tenant.create(TENANT_B, new TenantSlug("sec-test-b"), "Tenant B", TenantType.ORGANIZATION));
        }
        upsert(PUBLIC_CLEAR, TenantId.PUBLIC, Tlp.CLEAR, RedistributionPolicy.PUBLIC_REDISTRIBUTABLE, "sec-clear");
        upsert(PUBLIC_GREEN, TenantId.PUBLIC, Tlp.GREEN, RedistributionPolicy.PUBLIC_REDISTRIBUTABLE, "sec-green");
        upsert(B_AMBER, TENANT_B, Tlp.AMBER, RedistributionPolicy.ATTRIBUTION_REQUIRED, "sec-b-amber");
        upsert(DEMO_INTERNAL_ONLY, DEMO, Tlp.AMBER, RedistributionPolicy.INTERNAL_ONLY, "sec-internal");
        upsert(PUBLIC_CLEAR_INTERNAL, TenantId.PUBLIC, Tlp.CLEAR, RedistributionPolicy.INTERNAL_ONLY, "sec-pub-int");
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
        assertThat(indicators
                        .findVisible(anonymous, IndicatorFilter.none(), null, 5000)
                        .items())
                .noneMatch(i -> i.id().equals(PUBLIC_CLEAR_INTERNAL));
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

        List<Indicator> page = indicators
                .findVisible(anonymous, IndicatorFilter.none(), null, 5000)
                .items();
        assertThat(page).isNotEmpty();
        assertThat(page).allSatisfy(i -> {
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

        List<Indicator> page = indicators
                .findVisible(demoView, IndicatorFilter.none(), null, 5000)
                .items();
        assertThat(page)
                .allSatisfy(i -> assertThat(i.ownerTenantId().equals(DEMO)
                                || i.ownerTenantId().isPublic())
                        .isTrue());
    }

    @Test
    void security3_crossTenantAccessYieldsNotFound() {
        Visibility tenantBView = Visibility.authenticated(TENANT_B);
        assertThat(indicators.findVisibleById(DEMO_INTERNAL_ONLY, tenantBView)).isEmpty();
        assertThat(indicators
                        .findVisible(tenantBView, IndicatorFilter.none(), null, 5000)
                        .items())
                .noneMatch(i -> i.ownerTenantId().equals(DEMO));
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

    private void upsert(IndicatorId id, TenantId owner, Tlp tlp, RedistributionPolicy policy, String name) {
        if (indicators.findById(id).isPresent()) {
            return;
        }
        String normalized = name + ".security.ctip-sample.net";
        IndicatorSourceSnapshot report = new IndicatorSourceSnapshot(
                mockSourceId,
                normalized,
                Confidence.of(60),
                Severity.MEDIUM,
                tlp,
                SEEN,
                SEEN,
                null,
                policy,
                1,
                SourceRecordStatus.ACTIVE,
                Set.of("security-test"));
        indicators.save(Indicator.create(
                new NewIndicatorCommand(
                        id,
                        owner,
                        new IocValue(IocType.DOMAIN, null, normalized, normalized),
                        report,
                        new Reputation(70)),
                new Sha256FingerprintStrategy()));
    }

    private static IndicatorId fixedId(String suffix) {
        return new IndicatorId(UUID.fromString("00000000-0000-0000-0000-0000000000" + suffix));
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

    /** 端點層測試用獨立 client IP,避免與條號 7 共用匿名限流 bucket。 */
    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder asTestIp(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder) {
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
}
