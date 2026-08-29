package com.ctip.application.search;

/**
 * 一次 reconciliation 的結果(docs/spec/13-platform-ops.md §13.7:比對 DB 與 ES 的筆數與版本並修正)。
 *
 * <p>{@code databaseCount} / {@code indexCountBefore} 是「筆數」比對的兩端;
 * 其餘三個是實際做出的修正,DoD M2-24 據此斷言差異真的被偵測到並修好。
 */
public record ReconciliationReport(
        long databaseCount, long indexCountBefore, int reindexedMissing, int reindexedStale, int deletedOrphans) {

    public boolean inSync() {
        return reindexedMissing == 0 && reindexedStale == 0 && deletedOrphans == 0;
    }
}
