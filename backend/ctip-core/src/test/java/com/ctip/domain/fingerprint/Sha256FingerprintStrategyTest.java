package com.ctip.domain.fingerprint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ctip.sdk.FingerprintAlgorithm;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class Sha256FingerprintStrategyTest {

    private final Sha256FingerprintStrategy strategy = new Sha256FingerprintStrategy();

    @Test
    void computesKnownSha256Vector() {
        assertThat(strategy.fingerprint("abc").hex())
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(strategy.algorithm()).isEqualTo(FingerprintAlgorithm.SHA256);
    }

    @Test
    void missingAlgorithmIsTranslatedToIllegalState() {
        assertThatThrownBy(() -> Sha256FingerprintStrategy.digestFor("NO-SUCH-ALGO"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void fingerprintValueObjectRejectsInvalidHex() {
        assertThatThrownBy(() -> new Fingerprint("XYZ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Fingerprint("ba7816")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Fingerprint(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
