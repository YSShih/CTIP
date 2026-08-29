package com.ctip.infrastructure.elasticsearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * 兩個容易靜默出錯的細節:排序鍵/版本的精度,以及使用者輸入的萬用字元跳脫。
 *
 * <p>ES 的 {@code date} 只有毫秒精度,而 keyset 分頁的鍵是 {@code (last_seen, id)}——
 * 截斷到毫秒會讓同一毫秒內的多筆資料在翻頁時被跳過(PostgreSQL 路徑的 {@code Cursor} 為此
 * 保留了奈秒);版本比對同理。跳脫則對應 {@code PostgresSearchAdapter} 對 {@code % _ \} 的處理:
 * 使用者打的 {@code *} 必須是字面值,不能變成 match-all。
 */
@Tag("unit")
class SearchQueryBuildingTest {

    @Test
    void epochNanosRoundTripsWithoutLosingSubMillisecondPrecision() {
        Instant instant = Instant.parse("2026-08-29T10:11:12.123456789Z");
        assertThat(EpochNanos.toInstant(EpochNanos.of(instant))).isEqualTo(instant);
    }

    @Test
    void epochNanosHandlesTimesBeforeTheEpoch() {
        Instant instant = Instant.parse("1969-12-31T23:59:59.500000000Z");
        assertThat(EpochNanos.toInstant(EpochNanos.of(instant))).isEqualTo(instant);
    }

    @Test
    void wildcardMetacharactersInUserInputAreEscaped() {
        assertThat(SearchTermQuery.escapeWildcard("*")).isEqualTo("\\*");
        assertThat(SearchTermQuery.escapeWildcard("a?b")).isEqualTo("a\\?b");
        assertThat(SearchTermQuery.escapeWildcard("a\\b")).isEqualTo("a\\\\b");
        assertThat(SearchTermQuery.escapeWildcard("mal-8.ctip-sample.net")).isEqualTo("mal-8.ctip-sample.net");
    }
}
