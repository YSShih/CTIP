package com.ctip.application.ingestion;

import com.ctip.domain.stix.StixProjection;
import java.util.ArrayList;
import java.util.List;

/**
 * 一個批次(一交易)的結果:計數與待寫出的 STIX 投影。
 * merged 為命中既有 indicator 而合併的筆數;projections 於交易提交後
 * 由 {@link IngestionBatchExecutor} 寫出(§7.8.6 失敗隔離)。
 */
public record BatchOutcome(int accepted, int rejected, int merged, List<StixProjection> projections) {

    public static final BatchOutcome EMPTY = new BatchOutcome(0, 0, 0, List.of());

    public BatchOutcome plus(BatchOutcome other) {
        List<StixProjection> combined = new ArrayList<>(projections);
        combined.addAll(other.projections);
        return new BatchOutcome(
                accepted + other.accepted, rejected + other.rejected, merged + other.merged, List.copyOf(combined));
    }
}
