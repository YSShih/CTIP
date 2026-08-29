package com.ctip.application.port;

import com.ctip.domain.indicator.IndicatorId;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * 送進搜尋索引的一筆文件(docs/spec/13-platform-ops.md §13.7 的搜尋欄位
 * <strong>加上可見度欄位</strong>)。真相來源永遠是 PostgreSQL,本型別只是它的投影。
 *
 * <p>⚠️ §13.7 的欄位清單<strong>不含</strong> {@code ownerTenantId} 與來源的再散布政策,
 * 但那些正是 {@code TlpSpecifications} 與 {@code IndicatorFilterSpecs} 的可見度與側信道防護
 * (ADR 0015、ADR 0020 §8)。少了它們,ES 路徑就會整套繞過過濾,因此本文件必須帶:
 *
 * <ul>
 *   <li>{@code ownerTenantId} — 租戶範圍(§7.7)
 *   <li>{@code redistributable} — 是否存在非 {@code INTERNAL_ONLY} 的來源記錄(I14 / §7.9 規則 3)
 *   <li>{@code disclosableSourceIds} — 可作為 {@code sourceId} 過濾條件的來源(ADR 0015 修正 2:
 *       否則逐一試 {@code sourceId} 就能還原被遮蔽的來源歸屬)
 * </ul>
 *
 * <p>軟刪除的 indicator <strong>不進索引</strong>(而非以旗標標記):不在索引裡就不可能被查出來,
 * 比多一個必須每次都記得加的過濾條件安全。reconciliation 會把殘留的孤兒文件刪掉。
 *
 * <p>{@code updatedAt} 是 reconciliation 的版本依據,取自 {@code indicators.updated_at}
 * (§13.7「比對 DB 與 ES 的筆數與版本」)。
 */
public record SearchIndexDocument(
        IndicatorId id,
        String ownerTenantId,
        String value,
        String normalizedValue,
        String type,
        String severity,
        String status,
        String tlp,
        int confidence,
        int score,
        Set<String> tags,
        Instant firstSeen,
        Instant lastSeen,
        Instant validUntil,
        boolean redistributable,
        Set<String> sourceIds,
        Set<String> disclosableSourceIds,
        Instant updatedAt) {

    public SearchIndexDocument {
        Objects.requireNonNull(id, "id");
        tags = tags == null ? Set.of() : Set.copyOf(tags);
        sourceIds = sourceIds == null ? Set.of() : Set.copyOf(sourceIds);
        disclosableSourceIds = disclosableSourceIds == null ? Set.of() : Set.copyOf(disclosableSourceIds);
    }

    /** 文件 id 一律是 indicator id 的字串形式,兩邊比對才有共同的鍵。 */
    public String documentId() {
        return id.value().toString();
    }
}
