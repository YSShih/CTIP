package com.ctip.application.source;

import com.ctip.application.ingestion.BatchOutcome;
import com.ctip.domain.source.SourceId;

/** 單一來源一次同步的結果摘要(排程與測試用;完整記錄在 source_sync 表)。 */
public record SourceSyncOutcome(
        SourceId sourceId,
        boolean success,
        int recordsFetched,
        int recordsAccepted,
        int recordsRejected,
        int recordsMerged,
        String errorMessage) {

    static SourceSyncOutcome success(SourceId sourceId, int recordsFetched, BatchOutcome totals) {
        return new SourceSyncOutcome(
                sourceId, true, recordsFetched, totals.accepted(), totals.rejected(), totals.merged(), null);
    }

    static SourceSyncOutcome failure(SourceId sourceId, int recordsFetched, String errorMessage) {
        return new SourceSyncOutcome(sourceId, false, recordsFetched, 0, 0, 0, errorMessage);
    }
}
