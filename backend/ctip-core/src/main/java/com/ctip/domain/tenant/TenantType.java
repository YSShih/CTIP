package com.ctip.domain.tenant;

/** 租戶型別(docs/spec/04-data-dictionary.md §4.5)。SYSTEM 僅限 public tenant(不變量 T4)。 */
public enum TenantType {
    SYSTEM,
    INDIVIDUAL,
    ORGANIZATION,
    ENTERPRISE
}
