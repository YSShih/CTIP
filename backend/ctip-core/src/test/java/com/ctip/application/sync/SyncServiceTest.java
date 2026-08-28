package com.ctip.application.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ctip.application.bloom.BloomTestHarness;
import com.ctip.application.plan.PlanLimitExceededException;
import com.ctip.domain.bloom.BloomBitArray;
import com.ctip.domain.bloom.BloomDeltaCodec;
import com.ctip.domain.bloom.BloomScope;
import com.ctip.domain.bloom.BloomVersion;
import com.ctip.domain.plan.Plan;
import com.ctip.domain.plan.PlanCode;
import com.ctip.domain.tenant.TenantId;
import com.ctip.testing.FakeSyncThrottle;
import com.ctip.testing.PlanFixtures;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * 同步讀取端的判定(docs/spec/11-sync-bloom.md §11.5、§11.6):
 * 方案授權、409 的三個入口、頻率限制、以及「delta 併集後仍能重現 resultingChecksum」。
 *
 * <p>HTTP 表述(base64url、狀態碼、標頭)由 {@code SyncEndToEndTest} 覆蓋;
 * 這裡只驗規則本身,不需要資料庫。
 */
@Tag("unit")
class SyncServiceTest {

    private static final String SUBJECT = "user:11111111-1111-1111-1111-111111111111";

    private final BloomTestHarness harness = new BloomTestHarness();
    private final FakeSyncThrottle throttle = new FakeSyncThrottle();
    private final SyncService sync =
            new SyncService(harness.ports, harness.quotas, harness.planner, harness.settings, throttle);

    @Test
    void theManifestDisclosesBothLayersAndWhatIsNotCovered() {
        harness.subscribe(BloomTestHarness.TENANT, PlanCode.PREMIUM);
        harness.members.add(BloomScope.PUBLIC, TenantId.PUBLIC, "public.example.net", BloomTestHarness.EARLIER);
        harness.members.add(
                BloomScope.TENANT, BloomTestHarness.TENANT, "private.example.net", BloomTestHarness.EARLIER);
        BloomVersion publicFull = harness.snapshots.generate(harness.planner.publicTarget());
        BloomVersion tenantFull = harness.snapshots.generate(
                harness.planner.tenantTarget(BloomTestHarness.TENANT).orElseThrow());

        SyncManifest manifest = sync.manifest(BloomTestHarness.TENANT);

        assertThat(manifest.publicBloom()).contains(publicFull);
        assertThat(manifest.tenantBloom()).contains(tenantFull);
        assertThat(manifest.notCovered()).containsExactly("TLP:GREEN");
        assertThat(manifest.maxDeltaChain()).isEqualTo(24);
    }

    /** §11.2 的 fail-closed:方案的 tenant_bloom_capacity 不是正整數就沒有 tenant 那一層。 */
    @Test
    void aPlanWithoutTenantBloomSeesNeitherTheLayerNorTheDownload() {
        harness.subscribe(BloomTestHarness.TENANT, PlanCode.FREE);
        harness.members.add(
                BloomScope.TENANT, BloomTestHarness.TENANT, "private.example.net", BloomTestHarness.EARLIER);

        assertThat(sync.manifest(BloomTestHarness.TENANT).tenantBloom()).isEmpty();
        assertThatThrownBy(() -> sync.download(BloomScope.TENANT, BloomTestHarness.TENANT, SUBJECT))
                .isInstanceOf(PlanLimitExceededException.class);
        assertThatThrownBy(() -> sync.delta(BloomScope.TENANT, BloomTestHarness.TENANT, 0, SUBJECT))
                .isInstanceOf(PlanLimitExceededException.class);
    }

    /** 匿名綁 public tenant,而 public tenant 沒有也不得有訂閱(T3)——tenant 那一層對它不存在。 */
    @Test
    void anonymousCallersHaveNoTenantLayer() {
        assertThat(sync.manifest(TenantId.PUBLIC).tenantBloom()).isEmpty();
        assertThatThrownBy(() -> sync.download(BloomScope.TENANT, TenantId.PUBLIC, SUBJECT))
                .isInstanceOf(PlanLimitExceededException.class);
    }

    @Test
    void aPlanWithPublicBloomDisabledDoesNotAdvertiseOrServeIt() {
        harness.plans.put(withoutPublicBloom(PlanCode.ANONYMOUS));
        harness.members.add(BloomScope.PUBLIC, TenantId.PUBLIC, "public.example.net", BloomTestHarness.EARLIER);
        harness.snapshots.generate(harness.planner.publicTarget());

        assertThat(sync.manifest(TenantId.PUBLIC).publicBloom()).isEmpty();
        assertThatThrownBy(() -> sync.download(BloomScope.PUBLIC, TenantId.PUBLIC, SUBJECT))
                .isInstanceOf(PlanLimitExceededException.class)
                .hasMessageContaining("Public bloom");
    }

    @Test
    void downloadingReturnsTheStoredArtifactAndConsumesTheInterval() {
        harness.members.add(BloomScope.PUBLIC, TenantId.PUBLIC, "public.example.net", BloomTestHarness.EARLIER);
        BloomVersion full = harness.snapshots.generate(harness.planner.publicTarget());

        BloomDownload download =
                sync.download(BloomScope.PUBLIC, TenantId.PUBLIC, SUBJECT).orElseThrow();

        assertThat(download.version()).isEqualTo(full);
        assertThat(download.content())
                .isEqualTo(harness.storage.readStored(full.artifact().storagePath()));
        assertThat(throttle.intervalOf(SUBJECT)).contains(Duration.ofSeconds(86_400));
        assertThat(harness.versions.downloadsOf(full.id()))
                .as("04 表 23 的 download_count 必須真的被記——有了下載端點它才有呼叫端")
                .isEqualTo(1);
        assertThatThrownBy(() -> sync.download(BloomScope.PUBLIC, TenantId.PUBLIC, SUBJECT))
                .isInstanceOf(SyncTooFrequentException.class)
                .satisfies(e ->
                        assertThat(((SyncTooFrequentException) e).retryAfter()).isEqualTo(Duration.ofSeconds(86_400)));
    }

    /** 還沒有任何 snapshot 時是 404(空 Optional),不是授權問題。 */
    @Test
    void downloadingBeforeTheFirstSnapshotFindsNothing() {
        assertThat(sync.download(BloomScope.PUBLIC, TenantId.PUBLIC, SUBJECT)).isEmpty();
    }

    /** 間隔已過的 client 可以再同步;間隔為 0 的方案完全不記帳。 */
    @Test
    void anElapsedIntervalAllowsTheNextSyncAndZeroMeansNoThrottling() {
        harness.members.add(BloomScope.PUBLIC, TenantId.PUBLIC, "public.example.net", BloomTestHarness.EARLIER);
        harness.snapshots.generate(harness.planner.publicTarget());
        throttle.recordSync(SUBJECT, BloomTestHarness.NOW.minusSeconds(86_401), Duration.ofSeconds(86_400));

        assertThat(sync.download(BloomScope.PUBLIC, TenantId.PUBLIC, SUBJECT)).isPresent();

        harness.plans.put(withoutSyncInterval(PlanCode.ANONYMOUS));
        String other = "ip:198.51.100.7";
        assertThat(sync.download(BloomScope.PUBLIC, TenantId.PUBLIC, other)).isPresent();
        assertThat(sync.download(BloomScope.PUBLIC, TenantId.PUBLIC, other)).isPresent();
        assertThat(throttle.lastSyncAt(other)).as("間隔 0 不記帳,否則永遠留著一筆不會過期的狀態").isEmpty();
    }

    /** 併集後的 payload 套用到 base 陣列上,必須重現 delta 回應宣告的 resultingChecksum。 */
    @Test
    void mergedDeltasReproduceTheAdvertisedResultingChecksum() {
        harness.plans.put(withoutSyncInterval(PlanCode.ANONYMOUS));
        harness.members.add(BloomScope.PUBLIC, TenantId.PUBLIC, "first.example.net", BloomTestHarness.EARLIER);
        BloomVersion full = harness.snapshots.generate(harness.planner.publicTarget());
        appendDelta("second.example.net");
        appendDelta("third.example.net");

        SyncDelta delta = sync.delta(BloomScope.PUBLIC, TenantId.PUBLIC, 0, SUBJECT);

        assertThat(delta.baseVersion()).isZero();
        assertThat(delta.targetVersion()).isEqualTo(2);
        assertThat(delta.addedMemberCount()).isEqualTo(2);
        BloomBitArray array = harness.read(full);
        BloomDeltaCodec.decode(delta.addedBits()).forEach(array::set);
        assertThat(array.checksum()).isEqualTo(delta.resultingChecksum());
        assertThat(delta.checksum().hex()).hasSize(64);
    }

    /** client 已是最新:空區間仍必須給得出可驗的 resultingChecksum(§11.5 必填)。 */
    @Test
    void aClientAtTheNewestVersionGetsAnEmptyButVerifiableDelta() {
        harness.plans.put(withoutSyncInterval(PlanCode.ANONYMOUS));
        harness.members.add(BloomScope.PUBLIC, TenantId.PUBLIC, "first.example.net", BloomTestHarness.EARLIER);
        BloomVersion full = harness.snapshots.generate(harness.planner.publicTarget());
        appendDelta("second.example.net");

        SyncDelta atOne = sync.delta(BloomScope.PUBLIC, TenantId.PUBLIC, 1, SUBJECT);
        assertThat(atOne.addedBits()).isEmpty();
        assertThat(atOne.targetVersion()).isEqualTo(1);
        assertThat(atOne.addedMemberCount()).isZero();

        BloomBitArray array = harness.read(full);
        BloomDeltaCodec.decode(harness.storage.read(
                        harness.versions
                                .findDeltaChain(BloomScope.PUBLIC, TenantId.PUBLIC, full.datasetVersion())
                                .get(0)
                                .artifact()
                                .storagePath(),
                        full.artifact().compression()))
                .forEach(array::set);
        assertThat(array.checksum()).isEqualTo(atOne.resultingChecksum());
    }

    @Test
    void withoutAnySnapshotTheOnlyAnswerIsSnapshotRequired() {
        assertThatThrownBy(() -> sync.delta(BloomScope.PUBLIC, TenantId.PUBLIC, 0, SUBJECT))
                .isInstanceOf(SnapshotRequiredException.class);
    }

    /** base 不在現行 dataset 的鏈上(通常是 client 的本地版本屬於舊 dataset)。 */
    @Test
    void aBaseOutsideTheCurrentChainAsksForAFullSnapshot() {
        harness.members.add(BloomScope.PUBLIC, TenantId.PUBLIC, "first.example.net", BloomTestHarness.EARLIER);
        harness.snapshots.generate(harness.planner.publicTarget());

        assertThatThrownBy(() -> sync.delta(BloomScope.PUBLIC, TenantId.PUBLIC, 7, SUBJECT))
                .isInstanceOf(SnapshotRequiredException.class)
                .hasMessageContaining("not part of the current dataset");
        assertThatThrownBy(() -> sync.delta(BloomScope.PUBLIC, TenantId.PUBLIC, -1, SUBJECT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * §11.3:鏈長超過政策上限 → 409。同時確認它<strong>不</strong>消耗同步間隔,
     * 否則 client 依 §11.6 轉去下載 full 時會立刻撞上 429,復原路徑永遠走不完。
     */
    @Test
    void aChainBeyondThePolicyAsksForAFullSnapshotWithoutConsumingTheInterval() {
        SyncService limited = new SyncService(
                harness.ports, harness.quotas, harness.planner, BloomTestHarness.settings(1, 3), throttle);
        harness.members.add(BloomScope.PUBLIC, TenantId.PUBLIC, "first.example.net", BloomTestHarness.EARLIER);
        harness.snapshots.generate(harness.planner.publicTarget());
        appendDelta("second.example.net");
        appendDelta("third.example.net");

        assertThatThrownBy(() -> limited.delta(BloomScope.PUBLIC, TenantId.PUBLIC, 0, SUBJECT))
                .isInstanceOf(SnapshotRequiredException.class)
                .hasMessageContaining("Delta chain too long");

        assertThat(throttle.lastSyncAt(SUBJECT)).isEmpty();
        assertThat(limited.download(BloomScope.PUBLIC, TenantId.PUBLIC, SUBJECT))
                .isPresent();
    }

    private void appendDelta(String value) {
        harness.members.add(BloomScope.PUBLIC, TenantId.PUBLIC, value, BloomTestHarness.NOW);
        harness.changes.markChanged(BloomScope.PUBLIC, TenantId.PUBLIC);
        assertThat(harness.deltas.generate(harness.planner.publicTarget()).version())
                .isNotNull();
    }

    private Plan withoutPublicBloom(PlanCode code) {
        return copyOf(PlanFixtures.of(code), false, PlanFixtures.of(code).minSyncIntervalSeconds());
    }

    private Plan withoutSyncInterval(PlanCode code) {
        return copyOf(PlanFixtures.of(code), true, 0);
    }

    private static Plan copyOf(Plan plan, boolean publicBloomEnabled, int minSyncIntervalSeconds) {
        return new Plan(
                plan.id(),
                plan.code(),
                plan.name(),
                plan.tier(),
                plan.requestsPerMinute(),
                plan.requestsPerDay(),
                plan.maxPageSize(),
                plan.maxBatchLookup(),
                minSyncIntervalSeconds,
                publicBloomEnabled,
                plan.tenantBloomCapacity(),
                plan.websocketEnabled(),
                plan.maxWebhooks(),
                plan.maxApiKeys(),
                plan.customFeedEnabled(),
                plan.stixExportMaxObjects(),
                plan.maxManualSubmissionsPerDay(),
                plan.maxImportRowsPerFile());
    }

    /** 只是為了讓「manifest 兩層皆空」也有明確表述:兩個 Optional 都空,notCovered 仍在。 */
    @Test
    void anEmptyPlatformStillDisclosesTheCoverageLimits() {
        SyncManifest manifest = sync.manifest(TenantId.PUBLIC);

        assertThat(manifest.publicBloom()).isEmpty();
        assertThat(manifest.tenantBloom()).isEqualTo(Optional.empty());
        assertThat(manifest.notCovered()).containsExactly("TLP:GREEN");
    }
}
