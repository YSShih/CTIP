package com.ctip.application.ingestion;

/** 一個批次(一交易)的計數結果;merged 為命中既有 indicator 而合併的筆數。 */
public record BatchOutcome(int accepted, int rejected, int merged) {

    public static final BatchOutcome EMPTY = new BatchOutcome(0, 0, 0);

    public BatchOutcome plus(BatchOutcome other) {
        return new BatchOutcome(accepted + other.accepted, rejected + other.rejected, merged + other.merged);
    }
}
