package com.ctip.domain.bloom;

import com.ctip.domain.event.BloomEvents.BloomSnapshotReady;
import com.ctip.domain.event.DomainEvent;
import com.ctip.domain.event.PendingEvents;
import com.ctip.domain.tenant.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 一份已生成的 Bloom(full snapshot 或 delta),聚合根
 * (docs/spec/02-ddd-model.md「BloomVersion」不變量 L1–L8;11 §11.3)。
 *
 * <p>不變量 L3(delta 的 base 必須指向同一 {@code (scope, tenantId, datasetVersion)} 內既存的版本)
 * 跨聚合實例,無法在單一聚合內判定:由 {@link #nextDelta} 保證「新 delta 一定接在既有版本之後」,
 * 生成服務再以 repository 查得的最新版本作為呼叫對象。
 *
 * <p>不變量 L8(命中永不代表確定惡意)不是資料條件,而是<strong>禁止存在把命中解讀為確定的程式碼</strong>;
 * 本聚合因此不提供任何 {@code isMalicious} 之類的查詢。
 */
public final class BloomVersion {

    private final BloomVersionSnapshot state;
    private final PendingEvents pendingEvents = new PendingEvents();

    private BloomVersion(BloomVersionSnapshot state) {
        this.state = validate(state);
    }

    private static BloomVersionSnapshot validate(BloomVersionSnapshot s) {
        Objects.requireNonNull(s.id(), "id 不得為 null");
        Objects.requireNonNull(s.scope(), "scope 不得為 null");
        Objects.requireNonNull(s.tenantId(), "tenantId 不得為 null");
        Objects.requireNonNull(s.parameters(), "parameters 不得為 null");
        Objects.requireNonNull(s.generatedAt(), "generatedAt 不得為 null");
        Objects.requireNonNull(s.artifact(), "artifact 不得為 null");
        if (s.scope() == BloomScope.PUBLIC && !s.tenantId().isPublic()) {
            throw new IllegalArgumentException("scope = PUBLIC 的 tenantId 必須是 public tenant(不變量 L1)");
        }
        if (s.scope() == BloomScope.TENANT && s.tenantId().isPublic()) {
            throw new IllegalArgumentException("scope = TENANT 不得綁在 public tenant 上(§11.2)");
        }
        if (s.fullSnapshot() != (s.baseBloomVersion() == null)) {
            throw new IllegalArgumentException("isFullSnapshot 與 baseBloomVersion IS NULL 必須等價(不變量 L2)");
        }
        if (s.fullSnapshot() != (s.artifact().resultingChecksum() == null)) {
            throw new IllegalArgumentException("delta 必須有 resultingChecksum、full 必須沒有(不變量 L6)");
        }
        requireVersionNumbers(s);
        return s;
    }

    private static void requireVersionNumbers(BloomVersionSnapshot s) {
        if (s.datasetVersion() < 1) {
            throw new IllegalArgumentException("datasetVersion 必須至少為 1:" + s.datasetVersion());
        }
        if (s.bloomVersion() < 0) {
            throw new IllegalArgumentException("bloomVersion 不得為負數:" + s.bloomVersion());
        }
        if (s.baseBloomVersion() != null && s.baseBloomVersion() >= s.bloomVersion()) {
            throw new IllegalArgumentException("delta 的 baseBloomVersion 必須小於自身版號");
        }
        if (s.memberCount() < 0) {
            throw new IllegalArgumentException("memberCount 不得為負數:" + s.memberCount());
        }
    }

    /** 平台的第一份 full snapshot({@code datasetVersion = 1}、{@code bloomVersion = 0})。 */
    public static BloomVersion firstSnapshot(BloomVersionSnapshot snapshot) {
        BloomVersion version = new BloomVersion(snapshot);
        version.requireFullSnapshot("firstSnapshot");
        version.recordReady();
        return version;
    }

    public static BloomVersion reconstitute(BloomVersionSnapshot snapshot) {
        return new BloomVersion(snapshot);
    }

    /**
     * 下一份 full snapshot:{@code datasetVersion + 1}、{@code bloomVersion} 歸零。
     *
     * <p>每日重建都會起新的 datasetVersion——這也是 L4(參數改變必須換 dataset)自動成立的原因:
     * 參數只在 full snapshot 決定,而 full snapshot 一定是新的 dataset。
     */
    public BloomVersion nextFullSnapshot(
            BloomVersionId id, BloomParameters parameters, long memberCount, BloomArtifact artifact, Instant at) {
        BloomVersion next = new BloomVersion(new BloomVersionSnapshot(
                id,
                state.scope(),
                state.tenantId(),
                state.datasetVersion() + 1,
                0L,
                parameters,
                memberCount,
                true,
                null,
                at,
                artifact));
        next.recordReady();
        return next;
    }

    /**
     * 下一份 delta:同一 dataset 內 {@code bloomVersion + 1},base 指向自己。
     *
     * <p>delta 只能「新增」位元:撤銷與過期的 IOC 無法透過 delta 移除(§11.3),
     * 只有 full snapshot 才會反映——這正是 datasetVersion 與 bloomVersion 兩個版號並存的唯一理由。
     */
    public BloomVersion nextDelta(BloomVersionId id, long memberCount, BloomArtifact artifact, Instant at) {
        return new BloomVersion(new BloomVersionSnapshot(
                id,
                state.scope(),
                state.tenantId(),
                state.datasetVersion(),
                state.bloomVersion() + 1,
                state.parameters(),
                memberCount,
                false,
                state.bloomVersion(),
                at,
                artifact));
    }

    /**
     * 套用<strong>到本版本為止</strong>,位元陣列應有的 SHA-256。
     *
     * <p>full 是 artifact 本身的 checksum;delta 是它的 {@code resultingChecksum}
     * (不變量 L6:full 沒有後者、delta 一定有)。manifest 用它表達「完全同步後你的陣列
     * 應該長什麼樣」,{@code /sync/delta} 用它讓 client 套用後自我驗證(§11.5、§11.6)
     * ——兩處若各自判斷 full/delta,任一邊寫錯就會讓所有 client 的驗證恆為失敗。
     */
    public Checksum arrayChecksum() {
        return state.fullSnapshot()
                ? state.artifact().checksum()
                : state.artifact().resultingChecksum();
    }

    /** L4:client 的本地參數與本版本不相容時,必須重下 full snapshot。 */
    public boolean isCompatibleWith(BloomParameters clientParameters) {
        return state.parameters().isCompatibleWith(clientParameters);
    }

    /**
     * §11.3:delta 鏈過長或累計 delta 過大時,client 應改下載 full snapshot。
     *
     * <p>必須對<strong>該鏈的 full snapshot</strong> 呼叫——比例的分母是完整位元陣列的大小。
     */
    public boolean requiresFullSnapshot(int chainLength, long cumulativeDeltaBytes, BloomChainPolicy policy) {
        requireFullSnapshot("requiresFullSnapshot");
        return chainLength > policy.maxDeltaChain()
                || cumulativeDeltaBytes > state.artifact().uncompressedSizeBytes() * policy.maxCumulativeDeltaRatio();
    }

    private void requireFullSnapshot(String operation) {
        if (!state.fullSnapshot()) {
            throw new IllegalStateException(operation + " 只適用於 full snapshot 版本");
        }
    }

    private void recordReady() {
        pendingEvents.record(new BloomSnapshotReady(
                state.tenantId(), state.scope(), state.datasetVersion(), state.bloomVersion(), state.memberCount()));
    }

    public List<DomainEvent> pullEvents() {
        return pendingEvents.pull();
    }

    public BloomVersionSnapshot snapshot() {
        return state;
    }

    public BloomVersionId id() {
        return state.id();
    }

    public BloomScope scope() {
        return state.scope();
    }

    public TenantId tenantId() {
        return state.tenantId();
    }

    public long datasetVersion() {
        return state.datasetVersion();
    }

    public long bloomVersion() {
        return state.bloomVersion();
    }

    public BloomParameters parameters() {
        return state.parameters();
    }

    public long memberCount() {
        return state.memberCount();
    }

    public boolean isFullSnapshot() {
        return state.fullSnapshot();
    }

    public BloomArtifact artifact() {
        return state.artifact();
    }

    public Instant generatedAt() {
        return state.generatedAt();
    }
}
