package com.ctip.domain.bloom;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Delta 的 {@code addedBits} 編碼(docs/spec/11-sync-bloom.md §11.5,<strong>強制</strong>)。
 *
 * <ol>
 *   <li>收集新增的 bit 索引,<strong>升序排序、去重</strong>
 *   <li>轉為差分序列:{@code d[0] = idx[0]}、{@code d[i] = idx[i] - idx[i-1]}
 *   <li>每個 {@code d[i]} 以 <strong>LEB128 unsigned varint</strong> 編碼
 *   <li>串接後 base64url(無 padding)
 * </ol>
 *
 * <p>本類別負責第 1–3 步(artifact 內容);第 4 步只在 HTTP 回應上發生,屬 Phase 16。
 * 04 表 23 的 {@code checksum} 對 delta 而言即為此 payload 的 SHA-256(§11.5 「sha256 of the
 * addedBits payload before base64」)。
 */
public final class BloomDeltaCodec {

    private BloomDeltaCodec() {}

    public static byte[] encode(List<Long> indices) {
        List<Long> sorted = indices.stream().distinct().sorted().toList();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        long previous = 0;
        for (long index : sorted) {
            if (index < 0) {
                throw new IllegalArgumentException("bit index 不得為負數:" + index);
            }
            writeVarint(out, index - previous);
            previous = index;
        }
        return out.toByteArray();
    }

    public static List<Long> decode(byte[] payload) {
        List<Long> indices = new ArrayList<>();
        long current = 0;
        int position = 0;
        while (position < payload.length) {
            long value = 0;
            int shift = 0;
            byte b;
            do {
                if (position >= payload.length) {
                    throw new IllegalArgumentException("varint 於位元組結尾被截斷");
                }
                if (shift > 63) {
                    throw new IllegalArgumentException("varint 超過 64 位元");
                }
                b = payload[position++];
                value |= (long) (b & 0x7F) << shift;
                shift += 7;
            } while ((b & 0x80) != 0);
            current += value;
            indices.add(current);
        }
        return indices;
    }

    private static void writeVarint(ByteArrayOutputStream out, long value) {
        long remaining = value;
        while ((remaining & ~0x7FL) != 0) {
            out.write((int) ((remaining & 0x7F) | 0x80));
            remaining >>>= 7;
        }
        out.write((int) remaining);
    }
}
