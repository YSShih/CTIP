package com.ctip;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctip.application.bloom.BloomArrayLoader;
import com.ctip.application.bloom.BloomChangeTracker;
import com.ctip.application.bloom.BloomDeltaService;
import com.ctip.application.bloom.BloomPorts;
import com.ctip.application.bloom.BloomScopePlanner;
import com.ctip.application.bloom.BloomSettings;
import com.ctip.application.bloom.BloomSnapshotService;
import com.ctip.application.bloom.BloomTarget;
import com.ctip.application.bloom.DeltaOutcome;
import com.ctip.application.port.BloomStoragePort;
import com.ctip.application.port.BloomVersionRepository;
import com.ctip.application.port.ClockPort;
import com.ctip.application.port.IdGeneratorPort;
import com.ctip.application.port.IndicatorRepository;
import com.ctip.application.port.PlanRepository;
import com.ctip.application.port.SourceRepository;
import com.ctip.application.port.SubscriptionRepository;
import com.ctip.application.port.TenantRepository;
import com.ctip.domain.bloom.BloomBitArray;
import com.ctip.domain.bloom.BloomChainPolicy;
import com.ctip.domain.bloom.BloomCompression;
import com.ctip.domain.bloom.BloomDeltaCodec;
import com.ctip.domain.bloom.BloomScope;
import com.ctip.domain.bloom.BloomVersion;
import com.ctip.domain.bloom.Checksum;
import com.ctip.domain.plan.PlanCode;
import com.ctip.domain.source.SourceId;
import com.ctip.domain.tenant.TenantId;
import com.ctip.sdk.RedistributionPolicy;
import com.ctip.sdk.SourceType;
import com.ctip.sdk.Tlp;
import com.ctip.support.BloomFixtures;
import com.ctip.support.BloomTenants;
import com.ctip.support.IndicatorFixtures;
import java.time.Instant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * DoD M2-14:delta 生成與套用正確,{@code resultingChecksum} 相符(§11.3、§11.5)。
 *
 * <p>核心斷言模擬 client 的 §11.6 流程:下載 full → 套用 delta → 驗證整個位元陣列的 checksum。
 * 不符即代表兩端算出的 bit index 不同,delta 機制整個不可用。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BloomDeltaTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private BloomSnapshotService snapshots;

    @Autowired
    private BloomDeltaService deltas;

    @Autowired
    private BloomScopePlanner planner;

    @Autowired
    private BloomVersionRepository versions;

    @Autowired
    private BloomStoragePort storage;

    @Autowired
    private BloomArrayLoader loader;

    @Autowired
    private BloomChangeTracker changes;

    @Autowired
    private BloomPorts ports;

    @Autowired
    private com.ctip.application.bloom.BloomGenerationService generation;

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

    private BloomTenants tenants;
    private SourceId sourceId;

    @BeforeAll
    void prepare() {
        tenants = new BloomTenants(tenantRepository, subscriptionRepository, planRepository, idGenerator, clock);
        sourceId = sources.findBySourceType(SourceType.MOCK_OPENPHISH)
                .orElseThrow()
                .id();
    }

    @Test
    void applyingTheDeltaToTheBaseArrayReproducesTheResultingChecksum() {
        BloomTarget target = target("bloom-delta-apply");
        BloomVersion full = snapshots.generate(target);
        addMember(target.tenantId(), "0000d101", "delta-added-member");

        DeltaOutcome outcome = deltas.generate(target);

        assertThat(outcome.status()).isEqualTo(DeltaOutcome.Status.CREATED);
        BloomVersion delta = outcome.version();
        assertThat(delta.datasetVersion()).isEqualTo(full.datasetVersion());
        assertThat(delta.bloomVersion()).isEqualTo(full.bloomVersion() + 1);
        assertThat(delta.snapshot().baseBloomVersion()).isEqualTo(full.bloomVersion());
        assertThat(delta.isFullSnapshot()).isFalse();

        // client:下載 full → 驗 checksum → 套用 delta → 驗 resultingChecksum(§11.6)
        BloomBitArray array = read(full);
        assertThat(array.checksum()).isEqualTo(full.artifact().checksum());
        assertThat(BloomFixtures.mightContain(
                        array, full.parameters(), BloomFixtures.fingerprintOf("delta-added-member")))
                .as("新成員在 full snapshot 之後才出現,base 陣列不該有它")
                .isFalse();

        byte[] payload =
                storage.read(delta.artifact().storagePath(), delta.artifact().compression());
        assertThat(Checksum.sha256(payload))
                .as("§11.5:delta 的 checksum 是 addedBits payload 的 SHA-256")
                .isEqualTo(delta.artifact().checksum());
        BloomDeltaCodec.decode(payload).forEach(array::set);

        assertThat(array.checksum()).isEqualTo(delta.artifact().resultingChecksum());
        assertThat(BloomFixtures.mightContain(
                        array, full.parameters(), BloomFixtures.fingerprintOf("delta-added-member")))
                .isTrue();
    }

    @Test
    void aScopeWithoutNewMembersDoesNotProduceAnEmptyDelta() {
        BloomTarget target = target("bloom-delta-quiet");
        BloomVersion full = snapshots.generate(target);

        assertThat(deltas.generate(target).status()).isEqualTo(DeltaOutcome.Status.NO_CHANGES);

        // 即使被標記為有變動,實際掃不到新成員也不建立版本——空 delta 會白白吃掉 chain 預算(§11.3)
        changes.markChanged(BloomScope.TENANT, target.tenantId());
        assertThat(deltas.generate(target).status()).isEqualTo(DeltaOutcome.Status.NO_CHANGES);
        assertThat(versions.findLatest(BloomScope.TENANT, target.tenantId())
                        .orElseThrow()
                        .id())
                .isEqualTo(full.id());
    }

    @Test
    void aChainThatGrewTooLongAsksForAFullSnapshotInstead() {
        BloomTarget target = target("bloom-delta-chain");
        snapshots.generate(target);
        BloomDeltaService limited = new BloomDeltaService(ports, chainLimitOfOne(), loader, changes);

        addMember(target.tenantId(), "0000d201", "delta-chain-one");
        assertThat(limited.generate(target).status()).isEqualTo(DeltaOutcome.Status.CREATED);
        addMember(target.tenantId(), "0000d202", "delta-chain-two");
        assertThat(limited.generate(target).status()).isEqualTo(DeltaOutcome.Status.CREATED);

        DeltaOutcome outcome = limited.generate(target);

        assertThat(outcome.status()).isEqualTo(DeltaOutcome.Status.FULL_SNAPSHOT_REQUIRED);
        assertThat(outcome.needsFullSnapshot()).isTrue();
    }

    @Test
    void aScopeWithoutAnyBaselineAsksForAFullSnapshot() {
        BloomTarget target = target("bloom-delta-fresh");

        DeltaOutcome outcome = deltas.generate(target);

        assertThat(outcome.status()).isEqualTo(DeltaOutcome.Status.NO_BASELINE);
        assertThat(outcome.needsFullSnapshot()).isTrue();
        assertThat(snapshots.generate(target).datasetVersion()).isEqualTo(1);
    }

    @Test
    void theHourlyEntryPointAppendsADeltaForScopesThatChanged() {
        BloomTarget target = target("bloom-delta-scheduled");
        BloomVersion full = snapshots.generate(target);
        addMember(target.tenantId(), "0000d301", "delta-scheduled-member");

        generation.runDeltas();

        BloomVersion latest =
                versions.findLatest(BloomScope.TENANT, target.tenantId()).orElseThrow();
        assertThat(latest.isFullSnapshot()).isFalse();
        assertThat(latest.datasetVersion()).isEqualTo(full.datasetVersion());
        assertThat(latest.snapshot().baseBloomVersion()).isEqualTo(full.bloomVersion());
    }

    private BloomSettings chainLimitOfOne() {
        return new BloomSettings(100_000L, 0.001, 10_000L, BloomCompression.NONE, BloomChainPolicy.of(1), 30);
    }

    private BloomTarget target(String slug) {
        TenantId tenantId = tenants.create(slug);
        tenants.assignPlan(tenantId, PlanCode.PREMIUM);
        return planner.tenantTarget(tenantId).orElseThrow();
    }

    /** 觀測時間必須晚於上一次生成,否則落在 delta 的水位之外(§11.3 的水位語意)。 */
    private void addMember(TenantId owner, String id, String name) {
        Instant seen = clock.now().plusSeconds(300);
        BloomFixtures.upsertSeenAt(
                indicators,
                sourceId,
                new IndicatorFixtures.Fixture(
                        BloomFixtures.id(id), owner, Tlp.AMBER, RedistributionPolicy.INTERNAL_ONLY, name),
                seen);
        changes.markChanged(BloomScope.TENANT, owner);
    }

    private BloomBitArray read(BloomVersion version) {
        return BloomBitArray.of(
                version.parameters(),
                storage.read(
                        version.artifact().storagePath(), version.artifact().compression()));
    }
}
