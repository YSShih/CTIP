package com.ctip.domain.bloom;

/**
 * Bloom 的兩層架構(docs/spec/11-sync-bloom.md §11.2)。
 *
 * <p>不為 {@code TLP:GREEN} 建立第三層:那需要新增一條發布路徑與「CDN 上的認證閘」,
 * 而 GREEN 目前零資料量——違反抽象判準(01 §1.7)。日後若 GREEN 量大,擴充點就是這個 enum。
 */
public enum BloomScope {

    /** 全體共用一份,可放 CDN;只含 {@code TLP:CLEAR}。 */
    PUBLIC,

    /** 單一租戶的私有 IOC({@code AMBER} / {@code AMBER_STRICT})。 */
    TENANT
}
