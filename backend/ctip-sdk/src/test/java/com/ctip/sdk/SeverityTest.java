package com.ctip.sdk;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class SeverityTest {

    @Test
    void orderIsInfoLowMediumHighCritical() {
        assertThat(Severity.INFO.ordinal()).isLessThan(Severity.LOW.ordinal());
        assertThat(Severity.LOW.ordinal()).isLessThan(Severity.MEDIUM.ordinal());
        assertThat(Severity.MEDIUM.ordinal()).isLessThan(Severity.HIGH.ordinal());
        assertThat(Severity.HIGH.ordinal()).isLessThan(Severity.CRITICAL.ordinal());
    }

    @Test
    void maxPicksTheHigherSide() {
        assertThat(Severity.max(Severity.INFO, Severity.HIGH)).isEqualTo(Severity.HIGH);
        assertThat(Severity.max(Severity.CRITICAL, Severity.LOW)).isEqualTo(Severity.CRITICAL);
        assertThat(Severity.max(Severity.MEDIUM, Severity.MEDIUM)).isEqualTo(Severity.MEDIUM);
    }
}
