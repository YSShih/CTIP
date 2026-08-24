package com.ctip.sdk;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class TlpTest {

    @Test
    void strictnessOrderIsClearGreenAmberAmberStrictRed() {
        assertThat(Tlp.CLEAR.ordinal()).isLessThan(Tlp.GREEN.ordinal());
        assertThat(Tlp.GREEN.ordinal()).isLessThan(Tlp.AMBER.ordinal());
        assertThat(Tlp.AMBER.ordinal()).isLessThan(Tlp.AMBER_STRICT.ordinal());
        assertThat(Tlp.AMBER_STRICT.ordinal()).isLessThan(Tlp.RED.ordinal());
    }

    @Test
    void strictestPicksTheStricterSide() {
        assertThat(Tlp.strictest(Tlp.CLEAR, Tlp.AMBER)).isEqualTo(Tlp.AMBER);
        assertThat(Tlp.strictest(Tlp.RED, Tlp.GREEN)).isEqualTo(Tlp.RED);
        assertThat(Tlp.strictest(Tlp.GREEN, Tlp.GREEN)).isEqualTo(Tlp.GREEN);
    }

    @Test
    void isNoStricterThanImplementsVisibilityComparison() {
        assertThat(Tlp.CLEAR.isNoStricterThan(Tlp.GREEN)).isTrue();
        assertThat(Tlp.GREEN.isNoStricterThan(Tlp.GREEN)).isTrue();
        assertThat(Tlp.AMBER.isNoStricterThan(Tlp.GREEN)).isFalse();
    }
}
