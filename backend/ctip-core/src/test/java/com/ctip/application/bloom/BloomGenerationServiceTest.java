package com.ctip.application.bloom;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctip.domain.bloom.BloomScope;
import com.ctip.domain.tenant.TenantId;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** 排程入口的編排規則(§11.3):delta 無法產生時改跑 full snapshot。 */
@Tag("unit")
class BloomGenerationServiceTest {

    private final BloomTestHarness harness = new BloomTestHarness();

    private final BloomGenerationService generation =
            new BloomGenerationService(harness.planner, harness.snapshots, harness.deltas, harness.retention);

    @Test
    void theDailyRunRebuildsEveryScopeAndAppliesRetention() {
        harness.members.add(BloomScope.PUBLIC, TenantId.PUBLIC, "a.example.net", BloomTestHarness.NOW);

        generation.runFullSnapshots();
        generation.runFullSnapshots();
        generation.runFullSnapshots();
        generation.runFullSnapshots();

        assertThat(harness.versions.findNewestFirst(BloomScope.PUBLIC, TenantId.PUBLIC, 50))
                .as("保留份數為 3")
                .hasSize(3);
    }

    @Test
    void anHourlyRunFallsBackToAFullSnapshotWhenThereIsNoBaseline() {
        harness.members.add(BloomScope.PUBLIC, TenantId.PUBLIC, "a.example.net", BloomTestHarness.NOW);

        generation.runDeltas();

        assertThat(harness.versions.findLatestFullSnapshot(BloomScope.PUBLIC, TenantId.PUBLIC))
                .get()
                .satisfies(version -> assertThat(version.datasetVersion()).isEqualTo(1));
    }

    @Test
    void anHourlyRunAppendsADeltaWhenTheScopeChanged() {
        harness.members.add(BloomScope.PUBLIC, TenantId.PUBLIC, "old.example.net", BloomTestHarness.EARLIER);
        harness.snapshots.generate(harness.planner.publicTarget());
        harness.members.add(BloomScope.PUBLIC, TenantId.PUBLIC, "new.example.net", BloomTestHarness.NOW);
        harness.changes.markChanged(BloomScope.PUBLIC, TenantId.PUBLIC);

        generation.runDeltas();

        assertThat(harness.versions.findLatest(BloomScope.PUBLIC, TenantId.PUBLIC))
                .get()
                .satisfies(latest -> {
                    assertThat(latest.isFullSnapshot()).isFalse();
                    assertThat(latest.bloomVersion()).isEqualTo(1);
                });
    }
}
