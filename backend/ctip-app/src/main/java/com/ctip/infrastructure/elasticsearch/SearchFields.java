package com.ctip.infrastructure.elasticsearch;

/**
 * 索引欄位名(docs/spec/13-platform-ops.md §13.7 的搜尋欄位 + 可見度欄位)。
 * 集中成常數:欄位名同時出現在 mapping、查詢與文件序列化三處,拼錯不會編譯失敗,只會靜默查不到。
 */
final class SearchFields {

    static final String ID = "id";
    static final String OWNER_TENANT_ID = "ownerTenantId";
    static final String VALUE = "value";
    static final String NORMALIZED_VALUE = "normalizedValue";
    static final String TYPE = "type";
    static final String SEVERITY = "severity";
    static final String STATUS = "status";
    static final String TLP = "tlp";
    static final String TAGS = "tags";
    static final String CONFIDENCE = "confidence";
    static final String SCORE = "score";
    static final String FIRST_SEEN = "firstSeen";
    static final String LAST_SEEN = "lastSeen";
    static final String VALID_UNTIL = "validUntil";

    /** keyset 分頁的排序鍵;date 型別的毫秒精度不足以重現 (last_seen, id) 的鍵,故另存 epoch 奈秒。 */
    static final String LAST_SEEN_NANOS = "lastSeenNanos";

    /** I14 / §7.9 規則 3:是否存在非 INTERNAL_ONLY 的來源記錄。 */
    static final String REDISTRIBUTABLE = "redistributable";

    static final String SOURCE_IDS = "sourceIds";

    /** ADR 0015 修正 2:可作為 sourceId 過濾條件的來源。 */
    static final String DISCLOSABLE_SOURCE_IDS = "disclosableSourceIds";

    /** reconciliation 的版本依據(indicators.updated_at 的 epoch 奈秒)。 */
    static final String UPDATED_AT_NANOS = "updatedAtNanos";

    private SearchFields() {}
}
