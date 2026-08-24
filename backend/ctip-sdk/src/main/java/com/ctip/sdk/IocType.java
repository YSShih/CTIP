package com.ctip.sdk;

/** IOC 型別(docs/spec/07-domain-intel.md §7.1)。FILE_HASH 搭配 {@link IocHashType} 說明演算法。 */
public enum IocType {
    IPV4,
    IPV6,
    DOMAIN,
    URL,
    FILE_HASH,
    EMAIL
}
