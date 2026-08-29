package com.ctip.domain.threat;

import java.util.Objects;

/**
 * 外部參照(docs/spec/02-ddd-model.md §2.3 H3、04 表 21)。
 * 值物件,持久化於 {@code threat_external_references}(不得存 JSONB——§4.0 白名單不含它)。
 *
 * <p>H3:{@code externalId} 與 {@code url} 至少有一個;兩者皆空的參照指不到任何東西。
 */
public record ExternalReference(String sourceName, String externalId, String url, String description) {

    public ExternalReference {
        Objects.requireNonNull(sourceName, "sourceName 不得為 null");
        sourceName = sourceName.trim();
        externalId = blankToNull(externalId);
        url = blankToNull(url);
        description = blankToNull(description);
        if (sourceName.isEmpty()) {
            throw new IllegalArgumentException("sourceName 不得為空白");
        }
        if (externalId == null && url == null) {
            throw new IllegalArgumentException("ExternalReference 至少要有 externalId 或 url 之一(不變量 H3)");
        }
    }

    /**
     * H4 的比較鍵:同一 Threat 內 (sourceName, externalId) 唯一。
     * null 的 externalId 與空字串同義——DB 端以 {@code COALESCE(external_id, '')} 的唯一索引強制
     * (PostgreSQL 的 UNIQUE 不去重 null;ADR 0020),兩邊語意必須一致。
     */
    public String identityKey() {
        return sourceName + " " + (externalId == null ? "" : externalId);
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
