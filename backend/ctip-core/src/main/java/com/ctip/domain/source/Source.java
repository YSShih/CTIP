package com.ctip.domain.source;

import com.ctip.domain.event.DomainEvent;
import com.ctip.domain.event.PendingEvents;
import com.ctip.domain.event.SourceEvents.SourceDegraded;
import com.ctip.domain.event.SourceEvents.SourceFailed;
import com.ctip.domain.event.SourceEvents.SourceRecovered;
import com.ctip.sdk.FetchContext;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Source 聚合根,不變量 S1–S6(docs/spec/02-ddd-model.md §2.3)。
 * S1 由 {@link Reputation} 強制;S5 由 {@link CredentialMasker} 於寫入前強制;
 * S6(config 不存憑證原文)強制於 adapter 設定層——config 刻意不屬於本聚合。
 */
public final class Source {

    private final SourceSnapshot identity;
    private Reputation reputation;
    private boolean enabled;
    private SourceHealth health;
    private String lastErrorMessage;
    private String nextCursor;
    private long totalRecordsIngested;
    private final PendingEvents pendingEvents = new PendingEvents();

    private Source(SourceSnapshot snapshot) {
        this.identity = Objects.requireNonNull(snapshot);
        this.reputation = Objects.requireNonNull(snapshot.reputation());
        this.enabled = snapshot.enabled();
        this.health = Objects.requireNonNull(snapshot.health());
        this.lastErrorMessage = snapshot.lastErrorMessage();
        this.nextCursor = snapshot.nextCursor();
        this.totalRecordsIngested = snapshot.totalRecordsIngested();
        if (!snapshot.syncable() && snapshot.health().status() != SourceStatus.ACTIVE) {
            throw new IllegalArgumentException("syncable=false 的來源恆為 ACTIVE(不變量 S4)");
        }
    }

    public static Source reconstitute(SourceSnapshot snapshot) {
        return new Source(snapshot);
    }

    /** S2:任一次成功 → ACTIVE 且失敗歸零;自 DEGRADED/FAILED 恢復時發 SourceRecovered。 */
    public void recordSuccess(int recordCount, Duration latency, Instant now) {
        requireSyncable();
        if (recordCount < 0) {
            throw new IllegalArgumentException("recordCount 不得為負");
        }
        SourceStatus before = health.status();
        this.health = health.afterSuccess(now, latency);
        this.totalRecordsIngested += recordCount;
        if ((before == SourceStatus.DEGRADED || before == SourceStatus.FAILED)
                && health.status() == SourceStatus.ACTIVE) {
            pendingEvents.record(new SourceRecovered(id()));
        }
    }

    /** 同步完成後保存來源自訂的續抓游標;抓到底(hasMore = false)時為 null,下次自頭開始。 */
    public void advanceCursor(String cursor) {
        requireSyncable();
        this.nextCursor = cursor;
    }

    /** S2:連續失敗 3 次 → DEGRADED、10 次 → FAILED(發 SourceFailed);S5:訊息先遮罩。 */
    public void recordFailure(String reason, Instant now) {
        requireSyncable();
        this.lastErrorMessage = CredentialMasker.mask(reason);
        SourceStatus before = health.status();
        this.health = health.afterFailure(now);
        if (health.status() == SourceStatus.DEGRADED && before != SourceStatus.DEGRADED) {
            pendingEvents.record(new SourceDegraded(id(), health.consecutiveFailures()));
        }
        if (health.status() == SourceStatus.FAILED && before != SourceStatus.FAILED) {
            pendingEvents.record(new SourceFailed(id(), health.consecutiveFailures()));
        }
    }

    /** S3:DISABLED 只能由管理員手動設定。 */
    public void disable() {
        this.enabled = false;
        this.health = new SourceHealth(
                SourceStatus.DISABLED,
                health.consecutiveFailures(),
                health.lastSyncAt(),
                health.lastSuccessAt(),
                health.lastFailureAt(),
                health.avgLatencyMs());
    }

    /** S3:離開 DISABLED 同樣只能手動;重新啟用即回 ACTIVE 並歸零失敗計數。 */
    public void enable() {
        this.enabled = true;
        this.health = new SourceHealth(
                SourceStatus.ACTIVE,
                0,
                health.lastSyncAt(),
                health.lastSuccessAt(),
                health.lastFailureAt(),
                health.avgLatencyMs());
    }

    /** 是否到期應同步;recommendedInterval 未提供時由排程器節奏決定(視為到期)。 */
    public boolean isDueForSync(Instant now) {
        if (!identity.syncable() || !enabled || health.status() == SourceStatus.DISABLED) {
            return false;
        }
        Instant lastSync = health.lastSyncAt();
        Duration interval = identity.recommendedInterval();
        if (lastSync == null || interval == null) {
            return true;
        }
        return !now.isBefore(lastSync.plus(interval));
    }

    private void requireSyncable() {
        if (!identity.syncable()) {
            throw new IllegalStateException("syncable=false 的來源不參與健康狀態轉換(不變量 S4)");
        }
    }

    public List<DomainEvent> pullEvents() {
        return pendingEvents.pull();
    }

    public SourceSnapshot snapshot() {
        return new SourceSnapshot(
                identity.id(),
                identity.sourceType(),
                identity.displayName(),
                identity.homepageUrl(),
                identity.defaultTlp(),
                identity.redistributionPolicy(),
                reputation,
                enabled,
                identity.syncable(),
                identity.recommendedInterval(),
                health,
                lastErrorMessage,
                nextCursor,
                totalRecordsIngested);
    }

    public SourceId id() {
        return identity.id();
    }

    public Reputation reputation() {
        return reputation;
    }

    public boolean enabled() {
        return enabled;
    }

    public SourceHealth health() {
        return health;
    }

    public String lastErrorMessage() {
        return lastErrorMessage;
    }

    public String nextCursor() {
        return nextCursor;
    }

    public long totalRecordsIngested() {
        return totalRecordsIngested;
    }

    /** 抓取輸入(docs/spec/08-ingestion-sdk.md §8.1):since = 上次成功時間、cursor = 續抓游標。 */
    public FetchContext fetchContext(Map<String, String> config, int maxRecords) {
        return new FetchContext(health.lastSuccessAt(), nextCursor, config, maxRecords);
    }
}
