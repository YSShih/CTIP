package com.ctip.domain.bloom;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Bloom 的位元陣列(docs/spec/11-sync-bloom.md §11.4,<strong>互通性關鍵</strong>)。
 *
 * <ul>
 *   <li>位元序:bit index {@code i} 位於 byte {@code i / 8},該 byte 內的第 {@code i % 8} 個
 *       <strong>最低有效位</strong>(LSB-first)
 *   <li>陣列長度:{@code ceil(bitSize / 8)} bytes
 *   <li>未使用的尾端位元必須為 0
 * </ul>
 *
 * <p>此格式排除所有現成 Bloom 函式庫(Guava 用 murmur3_128 與自有 layout),
 * 06 §6.2.2 因此明列「自行實作」。
 */
public final class BloomBitArray {

    private final long bitSize;
    private final byte[] bits;

    private BloomBitArray(long bitSize, byte[] bits) {
        this.bitSize = bitSize;
        this.bits = bits;
    }

    public static BloomBitArray empty(BloomParameters parameters) {
        Objects.requireNonNull(parameters, "parameters 不得為 null");
        return new BloomBitArray(parameters.bitSize(), new byte[parameters.byteLength()]);
    }

    /** 由既有位元組重建;長度與尾端位元皆須符合 §11.4,否則拒絕(壞掉的 artifact 不得靜默使用)。 */
    public static BloomBitArray of(BloomParameters parameters, byte[] content) {
        Objects.requireNonNull(parameters, "parameters 不得為 null");
        Objects.requireNonNull(content, "content 不得為 null");
        if (content.length != parameters.byteLength()) {
            throw new IllegalArgumentException("位元陣列長度應為 " + parameters.byteLength() + " bytes,實際 " + content.length);
        }
        BloomBitArray array = new BloomBitArray(parameters.bitSize(), content.clone());
        array.requireTrailingBitsZero();
        return array;
    }

    private void requireTrailingBitsZero() {
        int unused = (int) (bits.length * 8L - bitSize);
        if (unused > 0 && (bits[bits.length - 1] & (0xFF << (8 - unused))) != 0) {
            throw new IllegalArgumentException("未使用的尾端位元必須為 0(§11.4)");
        }
    }

    public long bitSize() {
        return bitSize;
    }

    public boolean get(long index) {
        requireInRange(index);
        return (bits[(int) (index >>> 3)] & (1 << (int) (index & 7))) != 0;
    }

    /** 設定該位元;回傳 true 代表此次呼叫才由 0 變 1(delta 只收集「新設」的位元)。 */
    public boolean set(long index) {
        requireInRange(index);
        int byteIndex = (int) (index >>> 3);
        int mask = 1 << (int) (index & 7);
        if ((bits[byteIndex] & mask) != 0) {
            return false;
        }
        bits[byteIndex] = (byte) (bits[byteIndex] | mask);
        return true;
    }

    /** 套用一組索引,回傳其中實際由 0 變 1 者(升序、去重)。 */
    public List<Long> setAll(long[] indices) {
        List<Long> added = new ArrayList<>();
        for (long index : indices) {
            if (set(index)) {
                added.add(index);
            }
        }
        added.sort(Long::compareTo);
        return added;
    }

    private void requireInRange(long index) {
        if (index < 0 || index >= bitSize) {
            throw new IndexOutOfBoundsException("bit index 超出範圍:" + index + " / " + bitSize);
        }
    }

    public byte[] toByteArray() {
        return bits.clone();
    }

    public Checksum checksum() {
        return Checksum.sha256(bits);
    }
}
