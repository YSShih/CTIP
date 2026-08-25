package com.ctip.application.ingestion;

/** 八種拒絕原因(docs/spec/07-domain-intel.md §7.3;對應 ingestion_rejections.reason 的 CHECK)。 */
public enum RejectionReason {
    /** 正規化失敗或格式驗證不通過;pipeline 非預期錯誤也歸入此類並記明細。 */
    MALFORMED_VALUE,
    /** 私有/保留位址,除非來源明示允許。 */
    PRIVATE_OR_RESERVED_IP,
    /** 命中良性網域 allowlist(僅 DOMAIN 型別、exact match;URL 不套用)。 */
    ALLOWLISTED_DOMAIN,
    /** URL &gt; 2048、domain &gt; 253、email &gt; 320。 */
    LENGTH_EXCEEDED,
    /** 雜湊長度與宣告的 IocHashType 不符。 */
    HASH_LENGTH_MISMATCH,
    /** 無法推斷型別且來源未宣告。 */
    UNKNOWN_TYPE,
    /** 同一批次內重複(第二次起)。 */
    DUPLICATE_IN_BATCH,
    /** 手動提交/匯入超出方案配額(Phase 14 起有實際來源;規則與測試先行)。 */
    QUOTA_EXCEEDED
}
