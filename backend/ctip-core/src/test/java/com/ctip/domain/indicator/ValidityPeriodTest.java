package com.ctip.domain.indicator;

import static com.ctip.testing.IndicatorTestBuilder.SOURCE_A;
import static com.ctip.testing.IndicatorTestBuilder.SOURCE_B;
import static com.ctip.testing.IndicatorTestBuilder.T0;
import static com.ctip.testing.IndicatorTestBuilder.report;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ctip.sdk.IocType;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * 三步 valid_until 計算(docs/spec/04-data-dictionary.md §4.6)與 ValidityPeriod 值物件。
 * 取代 v1.1「任一來源為 null 則結果為 null」的語意——那會讓多數 IOC 永不過期。
 */
@Tag("unit")
class ValidityPeriodTest {

    @Test
    void sourceWithoutExplicitValidUntilFallsBackToTypeDefaultTtl() {
        IndicatorSource record = new IndicatorSource(report(SOURCE_A).build());
        assertThat(record.effectiveValidUntil(IocType.DOMAIN)).isEqualTo(T0.plus(Duration.ofDays(90)));
        assertThat(record.effectiveValidUntil(IocType.URL)).isEqualTo(T0.plus(Duration.ofDays(90)));
        assertThat(record.effectiveValidUntil(IocType.EMAIL)).isEqualTo(T0.plus(Duration.ofDays(90)));
        assertThat(record.effectiveValidUntil(IocType.IPV4)).isEqualTo(T0.plus(Duration.ofDays(30)));
        assertThat(record.effectiveValidUntil(IocType.IPV6)).isEqualTo(T0.plus(Duration.ofDays(30)));
    }

    @Test
    void fileHashWithoutExplicitValidUntilNeverExpires() {
        IndicatorSource record = new IndicatorSource(report(SOURCE_A).build());
        assertThat(record.effectiveValidUntil(IocType.FILE_HASH)).isNull();
        assertThat(IndicatorMergePolicy.aggregateValidUntil(List.of(record), IocType.FILE_HASH))
                .isNull();
    }

    @Test
    void explicitSourceValidUntilTakesPrecedenceOverDefaultTtl() {
        Instant explicit = T0.plus(Duration.ofDays(7));
        IndicatorSource record =
                new IndicatorSource(report(SOURCE_A).validUntil(explicit).build());
        assertThat(record.effectiveValidUntil(IocType.DOMAIN)).isEqualTo(explicit);
        assertThat(record.effectiveValidUntil(IocType.FILE_HASH)).isEqualTo(explicit);
    }

    @Test
    void aggregateTakesMaxOfEffectiveValidUntil() {
        Instant early = T0.plus(Duration.ofDays(7));
        List<IndicatorSource> records = List.of(
                new IndicatorSource(report(SOURCE_A).validUntil(early).build()),
                new IndicatorSource(report(SOURCE_B).build()));
        // MAX(7 天明示, T0 + 90 天預設) = T0 + 90 天
        assertThat(IndicatorMergePolicy.aggregateValidUntil(records, IocType.DOMAIN))
                .isEqualTo(T0.plus(Duration.ofDays(90)));
    }

    @Test
    void nullEffectiveValidUntilDoesNotNullifyAggregate() {
        Instant explicit = T0.plus(Duration.ofDays(30));
        List<IndicatorSource> records = List.of(
                new IndicatorSource(report(SOURCE_A).build()), // FILE_HASH 預設 TTL 為 null
                new IndicatorSource(report(SOURCE_B).validUntil(explicit).build()));
        // 不是 v1.1 的「任一為 null 則 null」:只有全部為 null 時結果才是 null
        assertThat(IndicatorMergePolicy.aggregateValidUntil(records, IocType.FILE_HASH))
                .isEqualTo(explicit);
    }

    @Test
    void validityPeriodAllowsNullUntilAndRejectsUntilNotAfterFrom() {
        assertThat(new ValidityPeriod(T0, null).until()).isNull();
        assertThatThrownBy(() -> new ValidityPeriod(T0, T0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ValidityPeriod(T0, T0.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validityPeriodExpiryIsExclusiveOfBoundary() {
        ValidityPeriod period = new ValidityPeriod(T0, T0.plus(Duration.ofDays(1)));
        assertThat(period.isExpiredAt(T0.plus(Duration.ofDays(1)))).isFalse();
        assertThat(period.isExpiredAt(T0.plus(Duration.ofDays(1)).plusMillis(1)))
                .isTrue();
        assertThat(new ValidityPeriod(T0, null).isExpiredAt(Instant.MAX)).isFalse();
    }
}
