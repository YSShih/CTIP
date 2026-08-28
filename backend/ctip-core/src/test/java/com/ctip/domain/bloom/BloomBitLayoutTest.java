package com.ctip.domain.bloom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ctip.domain.fingerprint.Fingerprint;
import com.ctip.sdk.FingerprintAlgorithm;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * 位元陣列格式(docs/spec/11-sync-bloom.md §11.4,<strong>互通性關鍵</strong>)。
 *
 * <p>本測試以固定的 fingerprint 輸入斷言<strong>確切的 byte 陣列</strong>——不是「有沒有命中」。
 * §11.4 存在的唯一理由就是「client 能產生位元組完全相同的陣列」,只驗命中等於沒有驗。
 */
@Tag("unit")
class BloomBitLayoutTest {

    private static final String ZERO_TAIL = "0".repeat(32);

    /** k = 3、m = 64 的手算案例:h1 = 1、h2 = 2 → 索引 1、3、5。 */
    private static final BloomParameters TINY = new BloomParameters(FingerprintAlgorithm.SHA256, 3, 64, 10, 0.01);

    @Test
    void bitIndexIsTheIthLeastSignificantBitOfByteIOverEight() {
        Fingerprint fingerprint = new Fingerprint("0000000000000001" + "0000000000000002" + ZERO_TAIL);

        BloomBitArray array = BloomBitArray.empty(TINY);
        array.setAll(BloomIndexer.indices(fingerprint, TINY));

        // 索引 1、3、5 → byte 0 的第 1、3、5 個最低有效位 = 0b0010_1010 = 0x2A(LSB-first)
        assertThat(array.toByteArray()).containsExactly(0x2A, 0, 0, 0, 0, 0, 0, 0);
        assertThat(BloomIndexer.indices(fingerprint, TINY)).containsExactly(1L, 3L, 5L);
    }

    @Test
    void theDoubleHashUsesUnsignedSixtyFourBitWraparound() {
        // h1 = -1、h2 = Long.MIN_VALUE + 1。i = 1 時 h1 + h2 溢位、i = 2 時 2 * h2 溢位。
        // 任意精度算術(例如 JavaScript BigInt 未截斷)會得到完全不同的索引 —— ADR 0019 定調為 wraparound。
        Fingerprint fingerprint = new Fingerprint("ffffffffffffffff" + "8000000000000001" + ZERO_TAIL);

        long[] indices = BloomIndexer.indices(fingerprint, TINY);

        assertThat(indices).containsExactly(63L, 0L, 1L);
        BloomBitArray array = BloomBitArray.empty(TINY);
        array.setAll(indices);
        assertThat(array.toByteArray()).containsExactly(0x03, 0, 0, 0, 0, 0, 0, 0x80);
    }

    @Test
    void theSpecificationExampleParametersAreDerivedFromTheFormula() {
        BloomParameters parameters = BloomParameters.forCapacity(FingerprintAlgorithm.SHA256, 10_000_000L, 0.001);

        // §11.5 的 manifest 範例:bitSize 143775880、sizeBytes 17971985;
        // hashFunctionCount 範例原寫 7,以公式為準更正為 10(ADR 0019)
        assertThat(parameters.bitSize()).isEqualTo(143_775_880L);
        assertThat(parameters.byteLength()).isEqualTo(17_971_985);
        assertThat(parameters.hashFunctionCount()).isEqualTo(10);
        assertThat(parameters.bitSize() % 8).isZero();
    }

    @Test
    void unusedTrailingBitsMustBeZero() {
        BloomParameters sixty = new BloomParameters(FingerprintAlgorithm.SHA256, 2, 60, 10, 0.01);

        assertThat(sixty.byteLength()).isEqualTo(8);
        assertThat(BloomBitArray.of(sixty, new byte[8]).bitSize()).isEqualTo(60);
        assertThatThrownBy(() -> BloomBitArray.of(sixty, new byte[] {0, 0, 0, 0, 0, 0, 0, (byte) 0x10}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("尾端位元");
    }

    @Test
    void settingReportsOnlyBitsThatFlippedFromZeroToOne() {
        BloomBitArray array = BloomBitArray.empty(TINY);

        assertThat(array.setAll(new long[] {5L, 1L, 5L})).containsExactly(1L, 5L);
        assertThat(array.setAll(new long[] {1L, 9L})).containsExactly(9L);
        assertThat(array.get(9L)).isTrue();
        assertThat(array.get(2L)).isFalse();
    }

    @Test
    void addedBitsAreVarintEncodedDifferencesOfTheSortedDistinctIndices() {
        // 索引 1、3、300 → 差分 1、2、297;297 = 0b100101001 → LEB128 = 0xA9 0x02
        byte[] payload = BloomDeltaCodec.encode(List.of(300L, 1L, 3L, 1L));

        assertThat(payload).containsExactly(0x01, 0x02, 0xA9, 0x02);
        assertThat(BloomDeltaCodec.decode(payload)).containsExactly(1L, 3L, 300L);
    }

    @Test
    void checksumIsTheSha256OfTheUncompressedArrayInLowercaseHex() {
        BloomBitArray array = BloomBitArray.empty(TINY);

        // 空陣列(8 個 0 byte)的 SHA-256
        assertThat(array.checksum().hex())
                .isEqualTo("af5570f5a1810b7af78caf4bc70a660f0df51e42baf91d4de5b2328de0e83dfc");
    }
}
