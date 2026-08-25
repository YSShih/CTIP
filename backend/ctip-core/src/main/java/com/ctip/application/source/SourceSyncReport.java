package com.ctip.application.source;

import java.time.Instant;
import java.util.UUID;

/** 一次同步結束時回寫 source_sync 的內容;errorMessage 已經過憑證遮罩。 */
public record SourceSyncReport(
        UUID sourceSyncId,
        SyncResult result,
        int recordsFetched,
        int recordsAccepted,
        int recordsRejected,
        int recordsMerged,
        Instant finishedAt,
        String errorMessage) {}
