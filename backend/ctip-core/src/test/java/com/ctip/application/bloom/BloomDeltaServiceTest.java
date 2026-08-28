package com.ctip.application.bloom;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctip.domain.bloom.BloomBitArray;
import com.ctip.domain.bloom.BloomDeltaCodec;
import com.ctip.domain.bloom.BloomScope;
import com.ctip.domain.bloom.BloomVersion;
import com.ctip.domain.bloom.Checksum;
import com.ctip.domain.fingerprint.Fingerprint;
import com.ctip.domain.tenant.TenantId;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Delta 的生成與套用(§11.3、§11.5)。核心是 client 的自我驗證路徑:
 * base 陣列套用 delta 之後的 checksum 必須等於 {@code resultingChecksum}。
 */
@Tag("unit")
class BloomDeltaServiceTest {

    private final BloomTestHarness harness = new BloomTestHarness();

    @Test
    void applyingTheDeltaReproducesTheResultingChecksum() {
        harness.members.add(BloomScope.PUBLIC, TenantId.PUBLIC, "old.example.net", BloomTestHarness.EARLIER);
        BloomVersion full = harness.snapshots.generate(harness.planner.publicTarget());
        Fingerprint added =
                harness.members.add(BloomScope.PUBLIC, TenantId.PUBLIC, "new.example.net", BloomTestHarness.NOW);
        harness.changes.markChanged(BloomScope.PUBLIC, TenantId.PUBLIC);

        DeltaOutcome outcome = harness.deltas.generate(harness.planner.publicTarget());

        assertThat(outcome.status()).isEqualTo(DeltaOutcome.Status.CREATED);
        BloomVersion delta = outcome.version();
        assertThat(delta.bloomVersion()).isEqualTo(1);
        assertThat(delta.snapshot().baseBloomVersion()).isEqualTo(0L);
        assertThat(delta.memberCount()).isEqualTo(2);

        BloomBitArray array = harness.read(full);
        assertThat(BloomTestHarness.mightContain(array, full.parameters(), added))
                .isFalse();
        byte[] payload = harness.storage.read(
                delta.artifact().storagePath(), delta.artifact().compression());
        assertThat(Checksum.sha256(payload)).isEqualTo(delta.artifact().checksum());
        BloomDeltaCodec.decode(payload).forEach(array::set);

        assertThat(array.checksum()).isEqualTo(delta.artifact().resultingChecksum());
        assertThat(BloomTestHarness.mightContain(array, full.parameters(), added))
                .isTrue();
    }

    @Test
    void anUnchangedScopeProducesNoVersionAtAll() {
        harness.members.add(BloomScope.PUBLIC, TenantId.PUBLIC, "old.example.net", BloomTestHarness.EARLIER);
        BloomVersion full = harness.snapshots.generate(harness.planner.publicTarget());

        assertThat(harness.deltas.generate(harness.planner.publicTarget()).status())
                .isEqualTo(DeltaOutcome.Status.NO_CHANGES);

        harness.changes.markChanged(BloomScope.PUBLIC, TenantId.PUBLIC);
        assertThat(harness.deltas.generate(harness.planner.publicTarget()).status())
                .as("被標記為有變動、但實際掃不到新成員時,不建立空 delta")
                .isEqualTo(DeltaOutcome.Status.NO_CHANGES);
        assertThat(harness.versions.findLatest(BloomScope.PUBLIC, TenantId.PUBLIC))
                .get()
                .satisfies(latest -> assertThat(latest.id()).isEqualTo(full.id()));
    }

    @Test
    void withoutAFullSnapshotThereIsNothingToAppendTo() {
        DeltaOutcome outcome = harness.deltas.generate(harness.planner.publicTarget());

        assertThat(outcome.status()).isEqualTo(DeltaOutcome.Status.NO_BASELINE);
        assertThat(outcome.needsFullSnapshot()).isTrue();
        assertThat(outcome.version()).isNull();
    }

    @Test
    void aChainLongerThanThePolicyDemandsAFullSnapshot() {
        BloomDeltaService limited =
                new BloomDeltaService(harness.ports, BloomTestHarness.settings(1, 3), harness.loader, harness.changes);
        harness.snapshots.generate(harness.planner.publicTarget());
        appendDelta(limited, "one.example.net");
        appendDelta(limited, "two.example.net");

        DeltaOutcome outcome = limited.generate(harness.planner.publicTarget());

        assertThat(outcome.status()).isEqualTo(DeltaOutcome.Status.FULL_SNAPSHOT_REQUIRED);
        assertThat(outcome.needsFullSnapshot()).isTrue();
    }

    @Test
    void parametersThatNoLongerMatchTheDatasetDemandAFullSnapshot() {
        harness.snapshots.generate(harness.planner.publicTarget());
        harness.changes.markChanged(BloomScope.PUBLIC, TenantId.PUBLIC);
        BloomTarget resized = new BloomTarget(
                BloomScope.PUBLIC,
                TenantId.PUBLIC,
                com.ctip.domain.bloom.BloomParameters.forCapacity(
                        com.ctip.sdk.FingerprintAlgorithm.SHA256, 50_000L, 0.01));

        DeltaOutcome outcome = harness.deltas.generate(resized);

        // 不變量 L4:bitSize / k / 演算法任一改變都必須新起 datasetVersion,不能再接 delta
        assertThat(outcome.status()).isEqualTo(DeltaOutcome.Status.FULL_SNAPSHOT_REQUIRED);
    }

    @Test
    void aCorruptedBaseArtifactDemandsAFullSnapshotInsteadOfPoisoningTheChain() {
        harness.members.add(BloomScope.PUBLIC, TenantId.PUBLIC, "old.example.net", BloomTestHarness.EARLIER);
        BloomVersion full = harness.snapshots.generate(harness.planner.publicTarget());
        harness.members.add(BloomScope.PUBLIC, TenantId.PUBLIC, "new.example.net", BloomTestHarness.NOW);
        harness.changes.markChanged(BloomScope.PUBLIC, TenantId.PUBLIC);
        harness.storage.corrupt(full.artifact().storagePath());

        DeltaOutcome outcome = harness.deltas.generate(harness.planner.publicTarget());

        // 以損壞的陣列算出的 resultingChecksum 會讓每個 client 套用後失敗,且查不出原因
        assertThat(outcome.status()).isEqualTo(DeltaOutcome.Status.FULL_SNAPSHOT_REQUIRED);
        assertThat(harness.versions.findDeltaChain(BloomScope.PUBLIC, TenantId.PUBLIC, full.datasetVersion()))
                .isEmpty();
    }

    private void appendDelta(BloomDeltaService service, String value) {
        harness.members.add(BloomScope.PUBLIC, TenantId.PUBLIC, value, BloomTestHarness.NOW);
        harness.changes.markChanged(BloomScope.PUBLIC, TenantId.PUBLIC);
        assertThat(service.generate(harness.planner.publicTarget()).status()).isEqualTo(DeltaOutcome.Status.CREATED);
    }
}
