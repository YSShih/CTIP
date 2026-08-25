package com.ctip.application.source;

import com.ctip.domain.source.SourceId;

/** 單一來源一次同步的結果摘要(排程與測試用;詳細記錄在 source_sync 表,Phase 6)。 */
public record SourceSyncOutcome(SourceId sourceId, boolean success, int recordsFetched, String errorMessage) {

    static SourceSyncOutcome success(SourceId sourceId, int recordsFetched) {
        return new SourceSyncOutcome(sourceId, true, recordsFetched, null);
    }

    static SourceSyncOutcome failure(SourceId sourceId, int recordsFetched, String errorMessage) {
        return new SourceSyncOutcome(sourceId, false, recordsFetched, errorMessage);
    }
}
