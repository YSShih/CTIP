package com.ctip.infrastructure.elasticsearch;

import java.time.Instant;

/**
 * {@link Instant} 與 epoch 奈秒的互轉。
 *
 * <p>ES 的 {@code date} 預設是毫秒精度,而 keyset 分頁的鍵是 {@code (last_seen, id)}——
 * 毫秒截斷會使同一毫秒內的多筆資料在翻頁時被跳過(Cursor 的 javadoc 已為 PostgreSQL 路徑
 * 記過同一件事)。版本比對同理:{@code updated_at} 是 timestamptz,毫秒化之後兩邊永遠對不齊。
 * 因此排序鍵與版本一律存成 {@code long}。
 */
final class EpochNanos {

    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    static long of(Instant instant) {
        return Math.addExact(Math.multiplyExact(instant.getEpochSecond(), NANOS_PER_SECOND), instant.getNano());
    }

    static Instant toInstant(long nanos) {
        return Instant.ofEpochSecond(Math.floorDiv(nanos, NANOS_PER_SECOND), Math.floorMod(nanos, NANOS_PER_SECOND));
    }

    private EpochNanos() {}
}
