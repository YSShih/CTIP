package com.ctip;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctip.application.bloom.BloomScopePlanner;
import com.ctip.application.bloom.BloomSnapshotService;
import com.ctip.application.port.BloomMemberPort;
import com.ctip.application.port.BloomStoragePort;
import com.ctip.application.port.ClockPort;
import com.ctip.application.port.IdGeneratorPort;
import com.ctip.application.port.IndicatorRepository;
import com.ctip.application.port.PlanRepository;
import com.ctip.application.port.SourceRepository;
import com.ctip.application.port.SubscriptionRepository;
import com.ctip.application.port.TenantRepository;
import com.ctip.domain.bloom.BloomBitArray;
import com.ctip.domain.bloom.BloomMembership;
import com.ctip.domain.bloom.BloomScope;
import com.ctip.domain.bloom.BloomVersion;
import com.ctip.domain.fingerprint.Fingerprint;
import com.ctip.domain.indicator.Indicator;
import com.ctip.domain.indicator.IndicatorId;
import com.ctip.domain.plan.PlanCode;
import com.ctip.domain.source.SourceId;
import com.ctip.domain.tenant.TenantId;
import com.ctip.sdk.RedistributionPolicy;
import com.ctip.sdk.SourceType;
import com.ctip.sdk.Tlp;
import com.ctip.support.BloomFixtures;
import com.ctip.support.BloomTenants;
import com.ctip.support.IndicatorFixtures;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * DoD M2-13:{@code TLP:GREEN} 的 IOC <strong>不在</strong> public bloom 中(不變量 L7、§11.2)。
 *
 * <p>public bloom 設計為可放 CDN、無租戶隔離,而 TLP 2.0 明確排除把 {@code GREEN} 放上
 * 公開可存取通道——因此 GREEN 沒有任何 Bloom 覆蓋(§11.1)。
 *
 * <p>本測試同時釘住「SQL 述詞 ≡ domain 述詞」:成員掃描在資料庫端、
 * {@link BloomMembership} 在 domain 端,兩邊各改一次就會出現安靜的漂移
 * ——public 少一筆是可用性問題,tenant 多一筆是跨租戶外洩。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BloomCoverageTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private BloomSnapshotService snapshots;

    @Autowired
    private BloomScopePlanner planner;

    @Autowired
    private BloomMemberPort members;

    @Autowired
    private IndicatorRepository indicators;

    @Autowired
    private SourceRepository sources;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private IdGeneratorPort idGenerator;

    @Autowired
    private ClockPort clock;

    @Autowired
    private BloomStoragePort storage;

    private TenantId tenantId;
    private TenantId otherTenantId;

    /** 名稱即 fixture 的識別:正規化值與指紋都由它推導。 */
    private static final String PUBLIC_CLEAR = "cov-public-clear";

    private static final String PUBLIC_GREEN = "cov-public-green";
    private static final String PUBLIC_INTERNAL = "cov-public-internal-only";
    private static final String PUBLIC_EXPIRED = "cov-public-expired";
    private static final String TENANT_AMBER = "cov-tenant-amber";
    private static final String TENANT_STRICT = "cov-tenant-amber-strict";
    private static final String TENANT_CLEAR = "cov-tenant-clear";
    private static final String OTHER_TENANT_AMBER = "cov-other-tenant-amber";

    @BeforeAll
    void seedFixtureMatrix() {
        BloomTenants tenants =
                new BloomTenants(tenantRepository, subscriptionRepository, planRepository, idGenerator, clock);
        tenantId = tenants.create("bloom-coverage");
        otherTenantId = tenants.create("bloom-coverage-other");
        tenants.assignPlan(tenantId, PlanCode.PREMIUM);
        tenants.assignPlan(otherTenantId, PlanCode.PREMIUM);

        upsert("0000c001", TenantId.PUBLIC, Tlp.CLEAR, RedistributionPolicy.PUBLIC_REDISTRIBUTABLE, PUBLIC_CLEAR);
        upsert("0000c002", TenantId.PUBLIC, Tlp.GREEN, RedistributionPolicy.PUBLIC_REDISTRIBUTABLE, PUBLIC_GREEN);
        upsert("0000c003", TenantId.PUBLIC, Tlp.CLEAR, RedistributionPolicy.INTERNAL_ONLY, PUBLIC_INTERNAL);
        upsert("0000c004", TenantId.PUBLIC, Tlp.CLEAR, RedistributionPolicy.PUBLIC_REDISTRIBUTABLE, PUBLIC_EXPIRED);
        upsert("0000c005", tenantId, Tlp.AMBER, RedistributionPolicy.INTERNAL_ONLY, TENANT_AMBER);
        upsert("0000c006", tenantId, Tlp.AMBER_STRICT, RedistributionPolicy.INTERNAL_ONLY, TENANT_STRICT);
        upsert("0000c007", tenantId, Tlp.CLEAR, RedistributionPolicy.PUBLIC_REDISTRIBUTABLE, TENANT_CLEAR);
        upsert("0000c008", otherTenantId, Tlp.AMBER, RedistributionPolicy.INTERNAL_ONLY, OTHER_TENANT_AMBER);
        BloomFixtures.expire(indicators, BloomFixtures.id("0000c004"));
    }

    private void upsert(String id, TenantId owner, Tlp tlp, RedistributionPolicy policy, String name) {
        SourceId source = sources.findBySourceType(SourceType.MOCK_OPENPHISH)
                .orElseThrow()
                .id();
        BloomFixtures.upsert(
                indicators, source, new IndicatorFixtures.Fixture(BloomFixtures.id(id), owner, tlp, policy, name));
    }

    @Test
    void greenIsNeverPartOfThePublicBloom() {
        BloomVersion version = snapshots.generate(planner.publicTarget());
        BloomBitArray array = load(version);

        assertThat(contains(array, version, PUBLIC_CLEAR)).isTrue();
        assertThat(contains(array, version, PUBLIC_GREEN))
                .as("TLP:GREEN 沒有任何 Bloom 覆蓋(不變量 L7、§11.1)")
                .isFalse();
        assertThat(contains(array, version, TENANT_AMBER)).isFalse();
        assertThat(contains(array, version, TENANT_STRICT)).isFalse();
    }

    @Test
    void thePublicBloomExcludesInternalOnlyAndInactiveIndicators() {
        BloomVersion version = snapshots.generate(planner.publicTarget());
        BloomBitArray array = load(version);

        assertThat(contains(array, version, PUBLIC_INTERNAL))
                .as("全部來源皆 INTERNAL_ONLY 者不得再散布(不變量 I14、§11.2)")
                .isFalse();
        assertThat(contains(array, version, PUBLIC_EXPIRED))
                .as("只有 ACTIVE 才是成員;過期只能靠 full snapshot 移除(§11.3)")
                .isFalse();
    }

    @Test
    void theTenantBloomHoldsPrivateSubmissionsEvenThoughTheyAreInternalOnly() {
        BloomVersion version = snapshots.generate(planner.tenantTarget(tenantId).orElseThrow());
        BloomBitArray array = load(version);

        // ADR 0019 的地雷:tenant 成員條件沒有再散布條件,沿用 eligibleForBloom() 會讓 tenant bloom 恆為空
        assertThat(contains(array, version, TENANT_AMBER)).isTrue();
        assertThat(contains(array, version, TENANT_STRICT)).isTrue();
        assertThat(contains(array, version, TENANT_CLEAR))
                .as("CLEAR 屬於 public 層,不重複放進 tenant bloom")
                .isFalse();
        assertThat(contains(array, version, OTHER_TENANT_AMBER))
                .as("他租戶的私有情資不得出現在本租戶的 bloom 中")
                .isFalse();
    }

    @Test
    void theSqlPredicateAgreesWithTheDomainPredicate() {
        Set<UUID> publicMembers = scan(BloomScope.PUBLIC, TenantId.PUBLIC);
        Set<UUID> tenantMembers = scan(BloomScope.TENANT, tenantId);

        for (String suffix : List.of(
                "0000c001", "0000c002", "0000c003", "0000c004", "0000c005", "0000c006", "0000c007", "0000c008")) {
            IndicatorId id = BloomFixtures.id(suffix);
            Indicator indicator = indicators.findById(id).orElseThrow();
            assertThat(publicMembers.contains(id.value()))
                    .as("public 述詞不一致:%s", suffix)
                    .isEqualTo(BloomMembership.inPublicBloom(indicator));
            assertThat(tenantMembers.contains(id.value()))
                    .as("tenant 述詞不一致:%s", suffix)
                    .isEqualTo(BloomMembership.inTenantBloom(indicator, tenantId));
        }
    }

    private Set<UUID> scan(BloomScope scope, TenantId owner) {
        return members.membersAfter(scope, owner, null, 5_000).stream()
                .map(BloomMemberPort.BloomMember::indicatorId)
                .collect(Collectors.toSet());
    }

    private BloomBitArray load(BloomVersion version) {
        return BloomBitArray.of(
                version.parameters(),
                storage.read(
                        version.artifact().storagePath(), version.artifact().compression()));
    }

    private boolean contains(BloomBitArray array, BloomVersion version, String name) {
        Fingerprint fingerprint = BloomFixtures.fingerprintOf(name);
        return BloomFixtures.mightContain(array, version.parameters(), fingerprint);
    }
}
