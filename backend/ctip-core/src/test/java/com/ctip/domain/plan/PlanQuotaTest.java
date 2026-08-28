package com.ctip.domain.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * 配額值的三種語意(docs/spec/10-identity-plans.md §10.6;ADR 0019)。
 *
 * <p>{@code 0} = 停用、{@code null} = 無限制、正整數 = 上限。把這三者塌陷成一個 long
 * 正是 phase-14 開工前必須先放寬三處型別的原因——ANONYMOUS 的 {@code max_api_keys = 0}
 * 會讓舊建構子在啟動時就丟例外。
 */
@Tag("unit")
class PlanQuotaTest {

    @Test
    void zeroMeansDisabledNotUnlimited() {
        QuotaLimit disabled = QuotaLimit.of(0);

        assertThat(disabled.isDisabled()).isTrue();
        assertThat(disabled.isUnlimited()).isFalse();
        assertThat(disabled.isExceededBy(1)).isTrue();
    }

    @Test
    void nullMeansUnlimited() {
        QuotaLimit unlimited = QuotaLimit.of((Integer) null);

        assertThat(unlimited.isUnlimited()).isTrue();
        assertThat(unlimited.isDisabled()).isFalse();
        assertThat(unlimited.isExceededBy(Long.MAX_VALUE)).isFalse();
        assertThat(unlimited.clamp(9999)).isEqualTo(9999);
    }

    @Test
    void clampNeverExceedsTheLimit() {
        assertThat(QuotaLimit.of(50).clamp(500)).isEqualTo(50);
        assertThat(QuotaLimit.of(50).clamp(10)).isEqualTo(10);
    }

    @Test
    void negativeQuotaIsRejected() {
        assertThatThrownBy(() -> new QuotaLimit(-1L)).isInstanceOf(IllegalArgumentException.class);
    }
}
