package com.ctip.infrastructure.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.ctip.application.port.SearchIndexDocument;
import java.io.IOException;
import java.io.StringReader;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 索引的定義與建立(docs/spec/13-platform-ops.md §13.7)。
 *
 * <p>mapping 是 {@code dynamic: strict}:欄位名打錯時寧可整筆寫入失敗,也不要靜默多出一個
 * 沒人查詢的欄位——那會讓可見度述詞看起來有寫、實際上比對到空值。
 *
 * <p>建立索引<strong>不在啟動時強制成功</strong>:ES 不可用時應用必須照常起來並降級
 * (§13.7「不得回 500」),因此建立是機會性的,成功後記住,失敗只記錄並在下次寫入前重試。
 */
public final class IndicatorSearchIndex {

    /** 索引名。§13.7 未指定,取與資料表對應的單一名稱;重建索引時整個刪掉重灌即可。 */
    static final String NAME = "ctip-indicators";

    private static final Logger log = LoggerFactory.getLogger(IndicatorSearchIndex.class);

    private static final String MAPPING = """
            {
              "mappings": {
                "dynamic": "strict",
                "properties": {
                  "id": { "type": "keyword" },
                  "ownerTenantId": { "type": "keyword" },
                  "value": { "type": "keyword", "ignore_above": 2048 },
                  "normalizedValue": { "type": "keyword", "ignore_above": 2048 },
                  "type": { "type": "keyword" },
                  "severity": { "type": "keyword" },
                  "status": { "type": "keyword" },
                  "tlp": { "type": "keyword" },
                  "tags": { "type": "keyword" },
                  "confidence": { "type": "integer" },
                  "score": { "type": "integer" },
                  "firstSeen": { "type": "date" },
                  "lastSeen": { "type": "date" },
                  "validUntil": { "type": "date" },
                  "lastSeenNanos": { "type": "long" },
                  "redistributable": { "type": "boolean" },
                  "sourceIds": { "type": "keyword" },
                  "disclosableSourceIds": { "type": "keyword" },
                  "updatedAtNanos": { "type": "long" }
                }
              }
            }
            """;

    private final ElasticsearchClient client;
    private volatile boolean present;

    public IndicatorSearchIndex(ElasticsearchClient client) {
        this.client = client;
    }

    /** 冪等:已存在就什麼都不做。回傳是否確認存在。 */
    public boolean ensureExists() {
        if (present) {
            return true;
        }
        try {
            if (client.indices().exists(e -> e.index(NAME)).value()) {
                present = true;
                return true;
            }
            client.indices().create(c -> c.index(NAME).withJson(new StringReader(MAPPING)));
            present = true;
            log.info("已建立搜尋索引 {}", NAME);
            return true;
        } catch (IOException | RuntimeException e) {
            log.warn("搜尋索引 {} 尚無法建立,將於下次寫入前重試", NAME, e);
            return false;
        }
    }

    /** 測試與重建用:忘記「索引已存在」的記憶(索引被外部刪除後必須重新建立)。 */
    public void forget() {
        present = false;
    }

    /**
     * 文件序列化。刻意輸出 {@code Map} 而非讓 JSON mapper 綁 record:
     * 欄位名與 mapping 必須逐字一致,攤在同一個方法裡最看得出漏了哪一個。
     */
    static Map<String, Object> toSource(SearchIndexDocument document) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put(SearchFields.ID, document.documentId());
        source.put(SearchFields.OWNER_TENANT_ID, document.ownerTenantId());
        source.put(SearchFields.VALUE, document.value());
        source.put(SearchFields.NORMALIZED_VALUE, document.normalizedValue());
        source.put(SearchFields.TYPE, document.type());
        source.put(SearchFields.SEVERITY, document.severity());
        source.put(SearchFields.STATUS, document.status());
        source.put(SearchFields.TLP, document.tlp());
        source.put(SearchFields.TAGS, List.copyOf(document.tags()));
        source.put(SearchFields.CONFIDENCE, document.confidence());
        source.put(SearchFields.SCORE, document.score());
        source.put(SearchFields.FIRST_SEEN, iso(document.firstSeen()));
        source.put(SearchFields.LAST_SEEN, iso(document.lastSeen()));
        source.put(SearchFields.VALID_UNTIL, iso(document.validUntil()));
        source.put(SearchFields.LAST_SEEN_NANOS, EpochNanos.of(document.lastSeen()));
        source.put(SearchFields.REDISTRIBUTABLE, document.redistributable());
        source.put(SearchFields.SOURCE_IDS, List.copyOf(document.sourceIds()));
        source.put(SearchFields.DISCLOSABLE_SOURCE_IDS, List.copyOf(document.disclosableSourceIds()));
        source.put(SearchFields.UPDATED_AT_NANOS, EpochNanos.of(document.updatedAt()));
        return source;
    }

    private static String iso(Instant instant) {
        return instant == null ? null : instant.toString();
    }
}
