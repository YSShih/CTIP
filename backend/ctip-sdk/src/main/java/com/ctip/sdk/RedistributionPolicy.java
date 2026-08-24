package com.ctip.sdk;

/** 再散布政策:該資料可否對外提供的法遵限制(docs/spec/07-domain-intel.md §7.9)。 */
public enum RedistributionPolicy {
    /** 可原樣對外提供。 */
    PUBLIC_REDISTRIBUTABLE,
    /** 可提供,但回應中必須附上來源標註。 */
    ATTRIBUTION_REQUIRED,
    /** 只能提供衍生結果(可回答「此 IP 有風險」,不得回傳原始記錄與來源明細)。 */
    DERIVED_ONLY,
    /** 不得對外輸出,僅供內部比對。 */
    INTERNAL_ONLY
}
