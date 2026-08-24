package com.ctip.domain.indicator;

/** Indicator 狀態(docs/spec/04-data-dictionary.md §4.5;判定順序見不變量 I11)。 */
public enum IndicatorStatus {
    ACTIVE,
    EXPIRED,
    REVOKED,
    FALSE_POSITIVE
}
