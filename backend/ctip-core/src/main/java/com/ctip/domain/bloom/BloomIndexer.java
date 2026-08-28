package com.ctip.domain.bloom;

import com.ctip.domain.fingerprint.Fingerprint;

/**
 * Kirsch-Mitzenmacher 雙雜湊索引(docs/spec/11-sync-bloom.md §11.4,<strong>強制</strong>)。
 *
 * <pre>
 * h1 = fingerprint 位元組的前 8 bytes,big-endian int64
 * h2 = 次 8 bytes,big-endian int64
 * 第 i 個索引 = ((h1 + i * h2) mod bitSize + bitSize) mod bitSize    (i = 0 .. k-1)
 * </pre>
 *
 * <p><strong>{@code h1 + i * h2} 以 unsigned 64-bit wraparound(mod 2^64)計算</strong>(ADR 0019)
 * ——即 Java {@code long} 的自然行為。規格寫的是數學上的 mod,而 JavaScript 的 {@code BigInt}
 * 不會 wrap,兩端算出的 index 會不同;非 Java client 必須自行截斷至 64 位元。
 *
 * <p>不得自行選用其他雜湊族(§11.7 契約 6)。
 */
public final class BloomIndexer {

    private static final int REQUIRED_BYTES = 16;

    private BloomIndexer() {}

    public static long[] indices(Fingerprint fingerprint, BloomParameters parameters) {
        byte[] digest = decodeHex(fingerprint.hex());
        long h1 = bigEndianLong(digest, 0);
        long h2 = bigEndianLong(digest, 8);
        long bitSize = parameters.bitSize();
        long[] indices = new long[parameters.hashFunctionCount()];
        for (int i = 0; i < indices.length; i++) {
            indices[i] = Math.floorMod(h1 + (long) i * h2, bitSize);
        }
        return indices;
    }

    private static byte[] decodeHex(String hex) {
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) ((digit(hex.charAt(i * 2)) << 4) | digit(hex.charAt(i * 2 + 1)));
        }
        if (out.length < REQUIRED_BYTES) {
            throw new IllegalArgumentException("fingerprint 至少需 " + REQUIRED_BYTES + " bytes 才能取雙雜湊");
        }
        return out;
    }

    private static int digit(char c) {
        int value = Character.digit(c, 16);
        if (value < 0) {
            throw new IllegalArgumentException("非十六進位字元:" + c);
        }
        return value;
    }

    private static long bigEndianLong(byte[] bytes, int offset) {
        long value = 0;
        for (int i = 0; i < 8; i++) {
            value = (value << 8) | (bytes[offset + i] & 0xFFL);
        }
        return value;
    }
}
