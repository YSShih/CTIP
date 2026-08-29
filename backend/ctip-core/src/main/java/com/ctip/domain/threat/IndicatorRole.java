package com.ctip.domain.threat;

/** IOC 在一個威脅中扮演的角色(docs/spec/04-data-dictionary.md §4.5、表 20)。 */
public enum IndicatorRole {
    C2,
    DELIVERY,
    PAYLOAD,
    INFRASTRUCTURE,
    VICTIM,
    UNKNOWN
}
