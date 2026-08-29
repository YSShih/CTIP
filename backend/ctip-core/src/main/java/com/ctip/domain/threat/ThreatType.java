package com.ctip.domain.threat;

/**
 * 威脅分類(docs/spec/04-data-dictionary.md §4.5)。
 * M2 只有 {@link #MALWARE_FAMILY} 與 {@link #ATTACK_PATTERN} 有對應的 STIX SDO(07 §7.8.1);
 * 其餘三型仍是平台的分類,只是不產生 STIX 物件。
 */
public enum ThreatType {
    CAMPAIGN,
    MALWARE_FAMILY,
    THREAT_ACTOR,
    ATTACK_PATTERN,
    PHISHING_KIT
}
