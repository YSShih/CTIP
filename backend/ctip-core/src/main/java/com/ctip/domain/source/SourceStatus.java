package com.ctip.domain.source;

/** 來源健康狀態(docs/spec/04-data-dictionary.md §4.5;狀態機見不變量 S2–S4)。 */
public enum SourceStatus {
    ACTIVE,
    DEGRADED,
    FAILED,
    DISABLED
}
