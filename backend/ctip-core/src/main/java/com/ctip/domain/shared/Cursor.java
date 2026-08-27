package com.ctip.domain.shared;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** cursor 分頁的不透明位置標記,對應 ix_indicators_last_seen 的複合排序鍵(docs/spec/02-ddd-model.md §2.6)。 */
public record Cursor(Instant lastSeen, UUID id) {

    public Cursor {
        Objects.requireNonNull(lastSeen, "lastSeen 不得為 null");
        Objects.requireNonNull(id, "id 不得為 null");
    }

    /**
     * canonical 內部編碼;API 層(Phase 9 CursorCodec)在此之上做對外的不透明包裝。
     * 保留完整奈秒精度——last_seen 為 TIMESTAMPTZ(微秒),截斷到毫秒會使 keyset
     * 條件漏掉頁界之後同毫秒內的資料列。
     */
    public String encode() {
        return lastSeen.getEpochSecond() + "." + lastSeen.getNano() + ":" + id;
    }

    public static Cursor decode(String encoded) {
        int separator = encoded.indexOf(':');
        if (separator <= 0) {
            throw new IllegalArgumentException("cursor 格式不符");
        }
        String timestamp = encoded.substring(0, separator);
        int dot = timestamp.indexOf('.');
        if (dot <= 0) {
            throw new IllegalArgumentException("cursor 格式不符");
        }
        return new Cursor(
                Instant.ofEpochSecond(
                        Long.parseLong(timestamp.substring(0, dot)), Long.parseLong(timestamp.substring(dot + 1))),
                UUID.fromString(encoded.substring(separator + 1)));
    }
}
