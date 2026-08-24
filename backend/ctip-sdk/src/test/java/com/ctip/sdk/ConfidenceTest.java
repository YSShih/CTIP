package com.ctip.sdk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class ConfidenceTest {

    @Test
    void acceptsBoundaryValues() {
        assertThat(Confidence.of(0).value()).isZero();
        assertThat(Confidence.of(100).value()).isEqualTo(100);
    }

    @Test
    void rejectsOutOfRangeValues() {
        assertThatThrownBy(() -> Confidence.of(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Confidence.of(101)).isInstanceOf(IllegalArgumentException.class);
    }
}
