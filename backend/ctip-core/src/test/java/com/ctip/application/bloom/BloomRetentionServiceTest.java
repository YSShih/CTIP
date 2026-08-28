package com.ctip.application.bloom;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctip.domain.bloom.BloomScope;
import com.ctip.domain.bloom.BloomVersion;
import com.ctip.domain.tenant.TenantId;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Artifact 保留(§11.3:最近 {@code BLOOM_ARTIFACT_KEEP} 份)。
 *
 * <p>關鍵在於<strong>不得刪掉還被 delta 依賴的 full snapshot</strong>:
 * 同一 dataset 內 full 的 bloomVersion 最小,照「保留最近 N 份」的字面實作會先刪到它。
 */
@Tag("unit")
class BloomRetentionServiceTest {

    private final BloomTestHarness harness = new BloomTestHarness();

    @Test
    void onlyTheConfiguredNumberOfVersionsSurvives() {
        BloomTarget target = harness.planner.publicTarget();
        for (int i = 0; i < 5; i++) {
            harness.snapshots.generate(target);
        }

        int deleted = harness.retention.purge(target);

        assertThat(deleted).isEqualTo(2);
        assertThat(harness.versions.findNewestFirst(BloomScope.PUBLIC, TenantId.PUBLIC, 50))
                .hasSize(3);
        assertThat(harness.storage.size()).isEqualTo(3);
    }

    @Test
    void nothingIsDeletedWhileTheCountIsWithinTheLimit() {
        BloomTarget target = harness.planner.publicTarget();
        harness.snapshots.generate(target);

        assertThat(harness.retention.purge(target)).isZero();
        assertThat(harness.storage.size()).isEqualTo(1);
    }

    @Test
    void aFullSnapshotIsKeptWhileItsOwnDeltasAreStillRetained() {
        BloomTarget target = harness.planner.publicTarget();
        BloomVersion full = harness.snapshots.generate(target);
        for (int i = 0; i < 3; i++) {
            harness.members.add(BloomScope.PUBLIC, TenantId.PUBLIC, "m" + i + ".example.net", BloomTestHarness.NOW);
            harness.changes.markChanged(BloomScope.PUBLIC, TenantId.PUBLIC);
            assertThat(harness.deltas.generate(target).status()).isEqualTo(DeltaOutcome.Status.CREATED);
        }

        // 保留 3 份 = 三段 delta;full 落在保留窗之外,但它的 dataset 仍有存活版本
        harness.retention.purge(target);

        assertThat(harness.versions.findLatestFullSnapshot(BloomScope.PUBLIC, TenantId.PUBLIC))
                .get()
                .satisfies(kept -> assertThat(kept.id()).isEqualTo(full.id()));
        assertThat(harness.loader.load(full, harness.versions.findDeltaChain(BloomScope.PUBLIC, TenantId.PUBLIC, 1)))
                .isNotNull();
    }

    @Test
    void purgingEveryPlannedScopeIsSafeWhenNothingHasBeenGeneratedYet() {
        harness.retention.purgeAll();

        assertThat(harness.storage.size()).isZero();
    }
}
