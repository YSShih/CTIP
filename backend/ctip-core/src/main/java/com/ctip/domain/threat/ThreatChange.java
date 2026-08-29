package com.ctip.domain.threat;

/**
 * {@code ThreatUpdated} 事件的變更種類(docs/spec/02-ddd-model.md §2.4)。
 * §2.4 對 Threat 只列了一個事件,故變更種類以本列舉承載,不另增事件型別;
 * 每個成員都由 {@link Threat} 的一個行為觸發(規則 16:不得有永不可達的成員)。
 */
public enum ThreatChange {
    CREATED,
    INDICATOR_LINKED,
    INDICATOR_UNLINKED,
    EXTERNAL_REFERENCE_ADDED,
    TLP_TIGHTENED,
    STATUS_CHANGED
}
