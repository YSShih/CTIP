package com.ctip.domain.stix;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * STIX 2.1 的時間字面值(docs/spec/07-domain-intel.md §7.8.2):ISO-8601、毫秒精度、{@code Z} 結尾。
 * 五種投影共用同一個格式,避免各自帶一份 pattern 而漂移。
 */
final class StixTimestamps {

    private static final DateTimeFormatter STIX_TIMESTAMP =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private StixTimestamps() {}

    static String format(Instant instant) {
        return STIX_TIMESTAMP.format(instant);
    }
}
