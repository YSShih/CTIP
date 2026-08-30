package com.ctip.infrastructure.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.IntStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** 讀取取樣(docs/spec/13-platform-ops.md §13.5 規則 4)。 */
@Tag("unit")
class AuditSamplerTest {

    @Test
    void aRateOfOneKeepsEverything() {
        AuditSampler sampler = new AuditSampler(1.0);

        assertThat(IntStream.range(0, 200).allMatch(i -> sampler.keepRead())).isTrue();
    }

    @Test
    void aRateOfZeroKeepsNothing() {
        AuditSampler sampler = new AuditSampler(0.0);

        assertThat(IntStream.range(0, 200).noneMatch(i -> sampler.keepRead())).isTrue();
    }

    /** 1% 是機率,不是配額:只驗它確實落在兩端之間,不驗確切筆數。 */
    @Test
    void theDefaultRateKeepsAMinorityOfReads() {
        AuditSampler sampler = new AuditSampler(0.01);

        long kept = IntStream.range(0, 10_000).filter(i -> sampler.keepRead()).count();

        assertThat(kept).isLessThan(500);
    }

    @Test
    void aRateOutsideZeroToOneIsRejected() {
        assertThatThrownBy(() -> new AuditSampler(1.5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuditSampler(-0.1)).isInstanceOf(IllegalArgumentException.class);
    }
}
