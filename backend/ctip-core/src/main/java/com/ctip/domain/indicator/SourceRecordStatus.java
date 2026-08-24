package com.ctip.domain.indicator;

/** 來源記錄狀態(docs/spec/04-data-dictionary.md §4.5)。RETRACTED = 來源明確撤回。 */
public enum SourceRecordStatus {
    ACTIVE,
    EXPIRED,
    RETRACTED,
    FALSE_POSITIVE
}
