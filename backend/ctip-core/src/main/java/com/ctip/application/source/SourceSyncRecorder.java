package com.ctip.application.source;

import com.ctip.application.ingestion.BatchOutcome;
import com.ctip.application.port.ClockPort;
import com.ctip.application.port.EventPublisherPort;
import com.ctip.application.port.SourceSyncLogPort;
import com.ctip.domain.event.IngestionEvents.IngestionCompleted;
import com.ctip.domain.event.IngestionEvents.IngestionFailed;
import com.ctip.domain.event.IngestionEvents.IngestionStarted;
import com.ctip.domain.source.CredentialMasker;
import com.ctip.domain.source.Source;
import com.ctip.domain.source.SourceId;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 一次同步的記錄面:source_sync 列(RUNNING → SUCCESS/PARTIAL/FAILURE)、
 * 來源健康、IngestionStarted/Completed/Failed 事件。錯誤訊息一律先遮罩(S5)。
 * 與 {@link SourceSyncService} 的抓取編排分離,各自單一職責。
 */
@Service
public class SourceSyncRecorder {

    private final SourceSyncLogPort syncLog;
    private final SourceHealthService health;
    private final EventPublisherPort events;
    private final ClockPort clock;

    public SourceSyncRecorder(
            SourceSyncLogPort syncLog, SourceHealthService health, EventPublisherPort events, ClockPort clock) {
        this.syncLog = syncLog;
        this.health = health;
        this.events = events;
        this.clock = clock;
    }

    /** 同步一開始就落一筆 RUNNING 列(finished_at null = 仍在執行或異常中斷)。 */
    public SyncRun started(SourceId sourceId) {
        Instant startedAt = clock.now();
        UUID syncId = syncLog.start(sourceId, startedAt);
        events.publish(new IngestionStarted(sourceId));
        return new SyncRun(syncId, startedAt);
    }

    public void completed(Source source, SyncRun run, int fetched, BatchOutcome totals, String nextCursor) {
        Instant finishedAt = clock.now();
        SyncResult result = totals.rejected() > 0 ? SyncResult.PARTIAL : SyncResult.SUCCESS;
        syncLog.finish(new SourceSyncReport(
                run.syncId(),
                result,
                fetched,
                totals.accepted(),
                totals.rejected(),
                totals.merged(),
                finishedAt,
                null));
        health.recordSuccess(source, fetched, Duration.between(run.startedAt(), finishedAt), nextCursor);
        events.publish(
                new IngestionCompleted(source.id(), fetched, totals.accepted(), totals.rejected(), totals.merged()));
    }

    /** 回傳遮罩後的錯誤訊息(供 outcome 使用)。 */
    public String failed(Source source, SyncRun run, int fetched, BatchOutcome totals, RuntimeException error) {
        String masked = CredentialMasker.mask(error.getMessage());
        syncLog.finish(new SourceSyncReport(
                run.syncId(),
                SyncResult.FAILURE,
                fetched,
                totals.accepted(),
                totals.rejected(),
                totals.merged(),
                clock.now(),
                masked));
        health.recordFailure(source, error.getMessage());
        events.publish(new IngestionFailed(source.id(), masked));
        return masked;
    }

    /** 一次執行的識別:source_sync 列 id 與開始時間。 */
    public record SyncRun(UUID syncId, Instant startedAt) {}
}
