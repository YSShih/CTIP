package com.ctip.application.bloom;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctip.domain.bloom.BloomBitArray;
import com.ctip.domain.bloom.BloomScope;
import com.ctip.domain.bloom.BloomVersion;
import com.ctip.domain.bloom.Checksum;
import com.ctip.domain.event.BloomEvents.BloomSnapshotReady;
import com.ctip.domain.fingerprint.Fingerprint;
import com.ctip.domain.plan.PlanCode;
import com.ctip.domain.tenant.TenantId;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Full snapshot 的生成(§11.3):零 false negative、checksum 相符、每次都起新的 datasetVersion。 */
@Tag("unit")
class BloomSnapshotServiceTest {

    private final BloomTestHarness harness = new BloomTestHarness();

    @Test
    void everyMemberIsPresentInTheGeneratedArray() {
        Fingerprint first =
                harness.members.add(BloomScope.PUBLIC, TenantId.PUBLIC, "a.example.net", BloomTestHarness.NOW);
        Fingerprint second =
                harness.members.add(BloomScope.PUBLIC, TenantId.PUBLIC, "b.example.net", BloomTestHarness.NOW);

        BloomVersion version = harness.snapshots.generate(harness.planner.publicTarget());

        BloomBitArray array = harness.read(version);
        assertThat(version.memberCount()).isEqualTo(2);
        assertThat(BloomTestHarness.mightContain(array, version.parameters(), first))
                .isTrue();
        assertThat(BloomTestHarness.mightContain(array, version.parameters(), second))
                .isTrue();
        assertThat(array.checksum()).isEqualTo(version.artifact().checksum());
        assertThat(Checksum.sha256(array.toByteArray()))
                .isEqualTo(version.artifact().checksum());
    }

    @Test
    void theFirstSnapshotAnnouncesItselfAndLaterOnesBumpTheDataset() {
        harness.members.add(BloomScope.PUBLIC, TenantId.PUBLIC, "a.example.net", BloomTestHarness.NOW);

        BloomVersion first = harness.snapshots.generate(harness.planner.publicTarget());
        BloomVersion second = harness.snapshots.generate(harness.planner.publicTarget());

        assertThat(first.datasetVersion()).isEqualTo(1);
        assertThat(second.datasetVersion()).isEqualTo(2);
        assertThat(second.bloomVersion()).isZero();
        assertThat(harness.events.published())
                .hasSize(2)
                .allSatisfy(event -> assertThat(event).isInstanceOf(BloomSnapshotReady.class));
    }

    @Test
    void generatingEveryPlannedScopeCoversPublicAndEntitledTenants() {
        harness.subscribe(BloomTestHarness.TENANT, PlanCode.PREMIUM);
        harness.members.add(BloomScope.PUBLIC, TenantId.PUBLIC, "a.example.net", BloomTestHarness.NOW);
        harness.members.add(BloomScope.TENANT, BloomTestHarness.TENANT, "private.example.net", BloomTestHarness.NOW);

        harness.planner.targets().forEach(harness.snapshots::generate);

        assertThat(harness.versions.findLatestFullSnapshot(BloomScope.PUBLIC, TenantId.PUBLIC))
                .isPresent();
        assertThat(harness.versions.findLatestFullSnapshot(BloomScope.TENANT, BloomTestHarness.TENANT))
                .get()
                .satisfies(version -> assertThat(version.memberCount()).isEqualTo(1));
        assertThat(harness.storage.size()).isEqualTo(2);
        // 生成後該 scope 視為乾淨,沒有新攝取就不會再產生 delta
        assertThat(harness.changes.hasChanges(BloomScope.PUBLIC, TenantId.PUBLIC))
                .isFalse();
    }

    @Test
    void aScopeWithNoMembersStillProducesAValidEmptySnapshot() {
        harness.subscribe(BloomTestHarness.TENANT, PlanCode.PREMIUM);
        harness.members.add(BloomScope.PUBLIC, TenantId.PUBLIC, "a.example.net", BloomTestHarness.NOW);

        harness.planner.targets().forEach(harness.snapshots::generate);

        assertThat(harness.versions.findLatestFullSnapshot(BloomScope.PUBLIC, TenantId.PUBLIC))
                .isPresent();
        assertThat(harness.versions.findLatestFullSnapshot(BloomScope.TENANT, BloomTestHarness.TENANT))
                .get()
                .satisfies(version -> assertThat(version.memberCount()).isZero());
    }
}
