package com.ctip.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** webhook 密鑰的 AES-GCM 加解密(不變量 W2 定調;ADR 0021)。 */
@Tag("unit")
class AesGcmSecretCipherTest {

    private static final String KEK = "unit-test-webhook-kek-0123456789abcdef";

    private final AesGcmSecretCipher cipher = new AesGcmSecretCipher(KEK);

    @Test
    void roundTripsTheSecret() {
        assertThat(cipher.decrypt(cipher.encrypt("s3cr3t-value"))).isEqualTo("s3cr3t-value");
    }

    /** GCM 在同一把金鑰下重用 nonce 會直接洩漏明文異或值——每次加密都必須是不同的密文。 */
    @Test
    void neverProducesTheSameCiphertextTwice() {
        byte[] first = cipher.encrypt("same-input");
        byte[] second = cipher.encrypt("same-input");
        assertThat(first).isNotEqualTo(second);
        assertThat(Arrays.copyOfRange(first, 0, 12)).isNotEqualTo(Arrays.copyOfRange(second, 0, 12));
    }

    /** 竄改密文必須被認證標籤擋下,不得回傳垃圾明文。 */
    @Test
    void rejectsTamperedCiphertext() {
        byte[] sealed = cipher.encrypt("s3cr3t-value");
        sealed[sealed.length - 1] ^= 0x01;
        assertThatThrownBy(() -> cipher.decrypt(sealed)).isInstanceOf(IllegalStateException.class);
    }

    /** 換過 KEK 之後解不開:這是「換金鑰要重建 webhook」的依據,不能靜默回垃圾。 */
    @Test
    void anotherKeyCannotDecrypt() {
        byte[] sealed = cipher.encrypt("s3cr3t-value");
        AesGcmSecretCipher other = new AesGcmSecretCipher("a-completely-different-kek-value-0123");
        assertThatThrownBy(() -> other.decrypt(sealed)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsAnEmptyKeyEncryptionKey() {
        assertThatThrownBy(() -> new AesGcmSecretCipher("  ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsTruncatedCiphertext() {
        assertThatThrownBy(() -> cipher.decrypt(new byte[8])).isInstanceOf(IllegalArgumentException.class);
    }
}
