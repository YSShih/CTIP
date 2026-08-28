package com.ctip;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctip.application.bloom.BloomPorts;
import com.ctip.application.bloom.BloomRetentionService;
import com.ctip.application.bloom.BloomScopePlanner;
import com.ctip.application.bloom.BloomSettings;
import com.ctip.application.bloom.BloomSnapshotService;
import com.ctip.application.bloom.BloomTarget;
import com.ctip.application.port.BloomMemberPort;
import com.ctip.application.port.BloomStoragePort;
import com.ctip.application.port.BloomVersionRepository;
import com.ctip.application.port.IndicatorRepository;
import com.ctip.application.port.SourceRepository;
import com.ctip.domain.bloom.BloomBitArray;
import com.ctip.domain.bloom.BloomChainPolicy;
import com.ctip.domain.bloom.BloomScope;
import com.ctip.domain.bloom.BloomStorageKind;
import com.ctip.domain.bloom.BloomVersion;
import com.ctip.domain.bloom.Checksum;
import com.ctip.domain.plan.PlanCode;
import com.ctip.domain.tenant.TenantId;
import com.ctip.sdk.RedistributionPolicy;
import com.ctip.sdk.SourceType;
import com.ctip.sdk.Tlp;
import com.ctip.support.BloomFixtures;
import com.ctip.support.BloomTenants;
import com.ctip.support.IndicatorFixtures;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * DoD M2-10（public 與 tenant bloom 皆可生成）與 M2-12（checksum 驗證通過）。
 *
 * <p>「生成成功」的判準不是有沒有寫出檔案,而是<strong>零 false negative</strong>:
 * 每一個成員都必須命中。Bloom 允許偽陽性,偽陰性則會讓 client 把惡意值判為「不在集合中」。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BloomGenerationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private BloomSnapshotService snapshots;

    @Autowired
    private BloomScopePlanner planner;

    @Autowired
    private BloomVersionRepository versions;

    @Autowired
    private BloomStoragePort storage;

    @Autowired
    private BloomMemberPort members;

    @Autowired
    private BloomPorts ports;

    @Autowired
    private com.ctip.application.bloom.BloomGenerationService generation;

    @Autowired
    private IndicatorRepository indicators;

    @Autowired
    private SourceRepository sources;

    @Autowired
    private com.ctip.application.port.TenantRepository tenantRepository;

    @Autowired
    private com.ctip.application.port.SubscriptionRepository subscriptionRepository;

    @Autowired
    private com.ctip.application.port.PlanRepository planRepository;

    @Autowired
    private com.ctip.application.port.IdGeneratorPort idGenerator;

    @Autowired
    private com.ctip.application.port.ClockPort clock;

    private BloomTenants tenants;

    private static final String PUBLIC_MEMBER = "gen-public-clear";
    private static final String TENANT_MEMBER = "gen-tenant-amber";

    private TenantId tenantId;

    @BeforeAll
    void seedMembers() {
        tenants = new BloomTenants(tenantRepository, subscriptionRepository, planRepository, idGenerator, clock);
        tenantId = tenants.create("bloom-generation");
        tenants.assignPlan(tenantId, PlanCode.PREMIUM);
        var sourceId = sources.findBySourceType(SourceType.MOCK_OPENPHISH)
                .orElseThrow()
                .id();
        BloomFixtures.upsert(
                indicators,
                sourceId,
                new IndicatorFixtures.Fixture(
                        BloomFixtures.id("0000b101"),
                        TenantId.PUBLIC,
                        Tlp.CLEAR,
                        RedistributionPolicy.PUBLIC_REDISTRIBUTABLE,
                        PUBLIC_MEMBER));
        // 私有提交的來源政策固定 INTERNAL_ONLY——tenant bloom 的成員條件不含再散布條件(ADR 0019)
        BloomFixtures.upsert(
                indicators,
                sourceId,
                new IndicatorFixtures.Fixture(
                        BloomFixtures.id("0000b102"),
                        tenantId,
                        Tlp.AMBER,
                        RedistributionPolicy.INTERNAL_ONLY,
                        TENANT_MEMBER));
    }

    @Test
    void thePublicSnapshotContainsEveryPublicMemberWithNoFalseNegatives() {
        BloomVersion version = snapshots.generate(planner.publicTarget());

        assertThat(version.scope()).isEqualTo(BloomScope.PUBLIC);
        assertThat(version.tenantId()).isEqualTo(TenantId.PUBLIC);
        assertThat(version.isFullSnapshot()).isTrue();
        assertThat(version.snapshot().baseBloomVersion()).isNull();
        assertThat(version.artifact().resultingChecksum()).isNull();
        assertThat(version.memberCount())
                .isEqualTo(members.countMembers(BloomScope.PUBLIC, TenantId.PUBLIC))
                .isPositive();

        BloomBitArray array = load(version);
        assertThat(BloomFixtures.mightContain(array, version.parameters(), BloomFixtures.fingerprintOf(PUBLIC_MEMBER)))
                .isTrue();
        assertNoFalseNegatives(array, version, BloomScope.PUBLIC, TenantId.PUBLIC);
    }

    @Test
    void aTenantSnapshotIsGeneratedForTenantsWhosePlanGrantsCapacity() {
        BloomTarget target = planner.tenantTarget(tenantId).orElseThrow();
        BloomVersion version = snapshots.generate(target);

        assertThat(version.scope()).isEqualTo(BloomScope.TENANT);
        assertThat(version.tenantId()).isEqualTo(tenantId);
        assertThat(version.memberCount()).isEqualTo(members.countMembers(BloomScope.TENANT, tenantId));

        BloomBitArray array = load(version);
        assertThat(BloomFixtures.mightContain(array, version.parameters(), BloomFixtures.fingerprintOf(TENANT_MEMBER)))
                .isTrue();
        assertNoFalseNegatives(array, version, BloomScope.TENANT, tenantId);
        // 容量是 min(方案上限, max(預設尺寸, 成員數));PREMIUM = 1,000,000、測試預設尺寸 10,000
        assertThat(version.parameters().capacity()).isEqualTo(10_000L);
    }

    @Test
    void theRecordedChecksumMatchesTheStoredArtifact() {
        BloomVersion version = snapshots.generate(planner.publicTarget());

        byte[] stored = storage.read(
                version.artifact().storagePath(), version.artifact().compression());

        assertThat(version.artifact().storageKind()).isEqualTo(BloomStorageKind.FILESYSTEM);
        assertThat(version.artifact().uncompressedSizeBytes())
                .isEqualTo(version.parameters().byteLength());
        assertThat(Checksum.sha256(stored)).isEqualTo(version.artifact().checksum());
        assertThat(version.artifact().checksum().hex()).matches("^[0-9a-f]{64}$");
    }

    @Test
    void everyFullSnapshotStartsANewDatasetVersion() {
        long before = snapshots.generate(planner.publicTarget()).datasetVersion();

        BloomVersion next = snapshots.generate(planner.publicTarget());

        assertThat(next.datasetVersion()).isEqualTo(before + 1);
        assertThat(next.bloomVersion()).isZero();
        assertThat(versions.findLatestFullSnapshot(BloomScope.PUBLIC, TenantId.PUBLIC)
                        .orElseThrow()
                        .id())
                .isEqualTo(next.id());
    }

    @Test
    void retentionKeepsOnlyTheConfiguredNumberOfVersions() {
        TenantId retained = tenants.create("bloom-retention");
        tenants.assignPlan(retained, PlanCode.PREMIUM);
        BloomTarget target = planner.tenantTarget(retained).orElseThrow();
        for (int i = 0; i < 4; i++) {
            snapshots.generate(target);
        }
        BloomRetentionService retention = new BloomRetentionService(ports, keepTwo(), planner);

        int deleted = retention.purge(target);

        assertThat(deleted).isEqualTo(2);
        assertThat(versions.findNewestFirst(BloomScope.TENANT, retained, 50)).hasSize(2);
    }

    @Test
    void theScheduledEntryPointRebuildsEveryScopeItPlans() {
        generation.runFullSnapshots();

        assertThat(versions.findLatestFullSnapshot(BloomScope.PUBLIC, TenantId.PUBLIC))
                .isPresent();
        assertThat(versions.findLatestFullSnapshot(BloomScope.TENANT, tenantId))
                .get()
                .satisfies(version -> assertThat(version.isFullSnapshot()).isTrue());
    }

    private BloomSettings keepTwo() {
        return new BloomSettings(
                100_000L, 0.001, 10_000L, com.ctip.domain.bloom.BloomCompression.NONE, BloomChainPolicy.of(24), 2);
    }

    private BloomBitArray load(BloomVersion version) {
        return BloomBitArray.of(
                version.parameters(),
                storage.read(
                        version.artifact().storagePath(), version.artifact().compression()));
    }

    private void assertNoFalseNegatives(BloomBitArray array, BloomVersion version, BloomScope scope, TenantId owner) {
        var page = members.membersAfter(scope, owner, null, 5_000);
        assertThat(page).isNotEmpty();
        assertThat(page)
                .allSatisfy(member -> assertThat(
                                BloomFixtures.mightContain(array, version.parameters(), member.fingerprint()))
                        .as("成員 %s 必須命中", member.indicatorId())
                        .isTrue());
    }
}
