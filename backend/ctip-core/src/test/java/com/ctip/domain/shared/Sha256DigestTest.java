package com.ctip.domain.shared;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** 憑證雜湊的共用純函式(§10.4、§10.5:資料庫只存雜湊)。 */
@Tag("unit")
class Sha256DigestTest {

    @Test
    void producesLowercaseHexOfSixtyFourCharacters() {
        String digest = Sha256Digest.hex("ctip");
        assertThat(digest).hasSize(64).matches("^[0-9a-f]{64}$");
    }

    /** 已知向量:空字串的 SHA-256。 */
    @Test
    void matchesTheKnownVectorForTheEmptyString() {
        assertThat(Sha256Digest.hex("")).isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    void isDeterministicAndCollisionFreeForDistinctInputs() {
        assertThat(Sha256Digest.hex("a")).isEqualTo(Sha256Digest.hex("a")).isNotEqualTo(Sha256Digest.hex("b"));
    }
}
