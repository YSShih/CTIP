package com.ctip.domain.bloom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ctip.domain.event.BloomEvents.BloomSnapshotReady;
import com.ctip.domain.tenant.TenantId;
import com.ctip.sdk.FingerprintAlgorithm;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** BloomVersion 聚合的不變量 L1–L6 與版號推進(docs/spec/02-ddd-model.md「BloomVersion」)。 */
@Tag("unit")
class BloomVersionTest {

    private static final TenantId TENANT = new TenantId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    private static final Instant AT = Instant.parse("2026-08-28T04:00:00Z");
    private static final BloomParameters PARAMS = new BloomParameters(FingerprintAlgorithm.SHA256, 3, 64, 10, 0.01);
    private static final Checksum SUM = Checksum.sha256(new byte[8]);

    private static BloomArtifact artifact(Checksum resulting, long uncompressed) {
        return new BloomArtifact(
                BloomStorageKind.FILESYSTEM,
                "public/1/0.bin",
                BloomCompression.NONE,
                8,
                uncompressed,
                SUM,
                resulting,
                0,
                null);
    }

    private static BloomVersionSnapshot full(BloomScope scope, TenantId tenant) {
        return new BloomVersionSnapshot(
                new BloomVersionId(UUID.randomUUID()),
                scope,
                tenant,
                1,
                0,
                PARAMS,
                3,
                true,
                null,
                AT,
                artifact(null, 1000));
    }

    @Test
    void publicScopeMustBeOwnedByThePublicTenant() {
        assertThatThrownBy(() -> BloomVersion.firstSnapshot(full(BloomScope.PUBLIC, TENANT)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("L1");
        assertThatThrownBy(() -> BloomVersion.firstSnapshot(full(BloomScope.TENANT, TenantId.PUBLIC)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("11.2");
    }

    @Test
    void fullSnapshotIsEquivalentToHavingNoBaseVersion() {
        BloomVersionSnapshot s = full(BloomScope.PUBLIC, TenantId.PUBLIC);
        BloomVersionSnapshot fullWithBase = new BloomVersionSnapshot(
                s.id(), s.scope(), s.tenantId(), 1, 1, PARAMS, 3, true, 0L, AT, artifact(null, 1000));

        assertThatThrownBy(() -> BloomVersion.reconstitute(fullWithBase))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("L2");
    }

    @Test
    void onlyDeltaCarriesAResultingChecksum() {
        BloomVersionSnapshot s = full(BloomScope.PUBLIC, TenantId.PUBLIC);
        BloomVersionSnapshot fullWithResulting = new BloomVersionSnapshot(
                s.id(), s.scope(), s.tenantId(), 1, 0, PARAMS, 3, true, null, AT, artifact(SUM, 1000));

        assertThatThrownBy(() -> BloomVersion.reconstitute(fullWithResulting))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("L6");
    }

    @Test
    void aFullSnapshotAnnouncesItselfAndStartsANewDataset() {
        BloomVersion first = BloomVersion.firstSnapshot(full(BloomScope.PUBLIC, TenantId.PUBLIC));

        assertThat(first.pullEvents()).singleElement().isInstanceOfSatisfying(BloomSnapshotReady.class, event -> {
            assertThat(event.scope()).isEqualTo(BloomScope.PUBLIC);
            assertThat(event.datasetVersion()).isEqualTo(1);
            assertThat(event.memberCount()).isEqualTo(3);
        });

        BloomVersion second =
                first.nextFullSnapshot(new BloomVersionId(UUID.randomUUID()), PARAMS, 9, artifact(null, 1000), AT);

        assertThat(second.datasetVersion()).isEqualTo(2);
        assertThat(second.bloomVersion()).isZero();
        assertThat(second.isFullSnapshot()).isTrue();
        assertThat(second.pullEvents()).hasSize(1);
    }

    @Test
    void aDeltaStaysInsideTheDatasetAndPointsAtItsBase() {
        BloomVersion first = BloomVersion.firstSnapshot(full(BloomScope.PUBLIC, TenantId.PUBLIC));

        BloomVersion delta = first.nextDelta(new BloomVersionId(UUID.randomUUID()), 5, artifact(SUM, 12), AT);

        assertThat(delta.datasetVersion()).isEqualTo(1);
        assertThat(delta.bloomVersion()).isEqualTo(1);
        assertThat(delta.isFullSnapshot()).isFalse();
        assertThat(delta.snapshot().baseBloomVersion()).isEqualTo(0L);
        assertThat(delta.parameters()).isEqualTo(PARAMS);
        assertThat(delta.pullEvents()).isEmpty();
    }

    @Test
    void clientParametersMustMatchBitSizeHashCountAndAlgorithm() {
        BloomVersion first = BloomVersion.firstSnapshot(full(BloomScope.PUBLIC, TenantId.PUBLIC));

        assertThat(first.isCompatibleWith(new BloomParameters(FingerprintAlgorithm.SHA256, 3, 64, 99, 0.5)))
                .isTrue();
        assertThat(first.isCompatibleWith(new BloomParameters(FingerprintAlgorithm.SHA256, 4, 64, 10, 0.01)))
                .isFalse();
        assertThat(first.isCompatibleWith(new BloomParameters(FingerprintAlgorithm.SHA512, 3, 64, 10, 0.01)))
                .isFalse();
    }

    /**
     * manifest 與 {@code /sync/delta} 的 {@code resultingChecksum} 都取這個值:
     * full 用 artifact 自己的 checksum,delta 用它套用後的陣列 checksum(L6)。
     * 兩處若各自判斷 full/delta,任一邊寫錯就會讓所有 client 的自我驗證恆為失敗。
     */
    @Test
    void theArrayChecksumFollowsWhetherTheVersionIsFullOrDelta() {
        BloomVersion first = BloomVersion.firstSnapshot(full(BloomScope.PUBLIC, TenantId.PUBLIC));
        Checksum applied = Checksum.sha256(new byte[16]);

        BloomVersion delta = first.nextDelta(new BloomVersionId(UUID.randomUUID()), 5, artifact(applied, 12), AT);

        assertThat(first.arrayChecksum()).isEqualTo(SUM);
        assertThat(delta.arrayChecksum()).isEqualTo(applied);
        assertThat(delta.artifact().checksum())
                .as("delta 的 artifact checksum 算的是 addedBits payload,不是陣列")
                .isEqualTo(SUM);
    }

    /** §11.5 的 coverage / notCovered 文字與成員條件同一處維護(BloomCoverage)。 */
    @Test
    void everyScopeDeclaresItsCoverageAndGreenIsCoveredByNothing() {
        assertThat(BloomCoverage.describe(BloomScope.PUBLIC)).isEqualTo("TLP:CLEAR only");
        assertThat(BloomCoverage.describe(BloomScope.TENANT)).contains("AMBER", "your tenant");
        assertThat(BloomCoverage.NOT_COVERED).containsExactly("TLP:GREEN");
    }

    @Test
    void aFullSnapshotIsRequiredWhenTheChainIsTooLongOrTooLarge() {
        BloomVersion first = BloomVersion.firstSnapshot(full(BloomScope.PUBLIC, TenantId.PUBLIC));
        BloomChainPolicy policy = BloomChainPolicy.of(24);

        assertThat(first.requiresFullSnapshot(24, 0, policy)).isFalse();
        assertThat(first.requiresFullSnapshot(25, 0, policy)).isTrue();
        assertThat(first.requiresFullSnapshot(1, 300, policy)).isFalse();
        assertThat(first.requiresFullSnapshot(1, 301, policy)).isTrue();

        BloomVersion delta = first.nextDelta(new BloomVersionId(UUID.randomUUID()), 5, artifact(SUM, 12), AT);
        assertThatThrownBy(() -> delta.requiresFullSnapshot(1, 1, policy)).isInstanceOf(IllegalStateException.class);
    }
}
