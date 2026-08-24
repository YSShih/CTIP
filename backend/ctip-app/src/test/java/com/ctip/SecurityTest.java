package com.ctip;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 安全測試 1、2、3、9(docs/spec/14-testing.md §14.4),Phase 4 於 repository/Specification 層驗證;
 * Phase 9 起同一批條號在 REST 端點層(404 語意)再驗一次。方法名含條號以便追溯。
 */
class SecurityTest extends AbstractPostgresIntegrationTest {

    private static final Instant SEEN = Instant.parse("2026-08-10T00:00:00Z");
    private static final TenantId DEMO = new TenantId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    private static final TenantId TENANT_B = new TenantId(UUID.fromString("00000000-0000-0000-0000-00000000000b"));

    private SourceId mockSourceId;

    private static final IndicatorId PUBLIC_GREEN = fixedId("41");
    private static final IndicatorId PUBLIC_CLEAR = fixedId("42");
    private static final IndicatorId B_AMBER = fixedId("43");
    private static final IndicatorId DEMO_INTERNAL_ONLY = fixedId("44");

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
    }

    @Test
    void security1_anonymousSeesOnlyPublicClear() {
        Visibility anonymous = Visibility.anonymous();
        assertThat(indicators.findVisibleById(PUBLIC_CLEAR, anonymous)).isPresent();
        assertThat(indicators.findVisibleById(PUBLIC_GREEN, anonymous)).isEmpty();
        assertThat(indicators.findVisibleById(B_AMBER, anonymous)).isEmpty();

        List<Indicator> page = indicators.findVisible(anonymous, null, 5000).items();
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

        List<Indicator> page = indicators.findVisible(demoView, null, 5000).items();
        assertThat(page)
                .allSatisfy(i -> assertThat(i.ownerTenantId().equals(DEMO)
                                || i.ownerTenantId().isPublic())
                        .isTrue());
    }

    @Test
    void security3_crossTenantAccessYieldsNotFound() {
        Visibility tenantBView = Visibility.authenticated(TENANT_B);
        assertThat(indicators.findVisibleById(DEMO_INTERNAL_ONLY, tenantBView)).isEmpty();
        assertThat(indicators.findVisible(tenantBView, null, 5000).items())
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
}
