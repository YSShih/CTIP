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

    /** canonical 內部編碼;API 層(Phase 9 CursorCodec)在此之上做對外的不透明包裝。 */
    public String encode() {
        return lastSeen.toEpochMilli() + ":" + id;
    }

    public static Cursor decode(String encoded) {
        int separator = encoded.indexOf(':');
        if (separator <= 0) {
            throw new IllegalArgumentException("cursor 格式不符");
        }
        return new Cursor(
                Instant.ofEpochMilli(Long.parseLong(encoded.substring(0, separator))),
                UUID.fromString(encoded.substring(separator + 1)));
    }
}
