package com.ctip.domain.bloom;

import com.ctip.sdk.FingerprintAlgorithm;
import java.util.Objects;

/**
 * 決定位元陣列內容的全部參數(docs/spec/11-sync-bloom.md §11.4)。
 *
 * <p>推導公式(強制):
 *
 * <pre>
 * m = ceil(-n * ln(p) / (ln 2)^2)   再向上取整至 8 的倍數
 * k = max(1, round((m / n) * ln 2))
 * </pre>
 *
 * <p>§11.5 的 manifest 範例原本寫 {@code hashFunctionCount = 7},但同一組參數
 * (n = 10,000,000、p = 0.001)代入公式得 10;範例的另外兩個數字都是公式算出來的,
 * 只有 k 不是。<strong>以公式為準</strong>(ADR 0019):k 取 7 或 10 會產出完全不同的
 * byte 陣列,而 §11.4 存在的理由正是「client 能產生位元組完全相同的陣列」。
 *
 * <p>不變量 L4:三者任一改變即須新起一個 {@code datasetVersion}——舊 client 的本地 Bloom 立即作廢。
 */
public record BloomParameters(
        FingerprintAlgorithm algorithm, int hashFunctionCount, long bitSize, long capacity, double falsePositiveRate) {

    private static final double LN2 = Math.log(2);
    private static final double LN2_SQUARED = LN2 * LN2;

    public BloomParameters {
        Objects.requireNonNull(algorithm, "algorithm 不得為 null");
        if (hashFunctionCount <= 0) {
            throw new IllegalArgumentException("hashFunctionCount 必須為正數:" + hashFunctionCount);
        }
        if (bitSize <= 0) {
            throw new IllegalArgumentException("bitSize 必須為正數:" + bitSize);
        }
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity 必須為正數:" + capacity);
        }
        if (falsePositiveRate <= 0 || falsePositiveRate >= 1) {
            throw new IllegalArgumentException("falsePositiveRate 必須介於 0 與 1 之間:" + falsePositiveRate);
        }
    }

    /** 依 §11.4 的公式推導;capacity 為預期成員數 n,falsePositiveRate 為目標 p。 */
    public static BloomParameters forCapacity(FingerprintAlgorithm algorithm, long capacity, double fpr) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity 必須為正數:" + capacity);
        }
        if (fpr <= 0 || fpr >= 1) {
            throw new IllegalArgumentException("falsePositiveRate 必須介於 0 與 1 之間:" + fpr);
        }
        long bits = roundUpToMultipleOfEight((long) Math.ceil(-capacity * Math.log(fpr) / LN2_SQUARED));
        int k = (int) Math.max(1, Math.round((double) bits / capacity * LN2));
        return new BloomParameters(algorithm, k, bits, capacity, fpr);
    }

    private static long roundUpToMultipleOfEight(long bits) {
        long positive = Math.max(8, bits);
        return (positive + 7) / 8 * 8;
    }

    /**
     * 未壓縮位元陣列的長度 {@code ceil(bitSize / 8)}(§11.4)。
     *
     * <p>{@link #forCapacity} 推導出的 m 一律是 8 的倍數,因此生成端不會有未使用的尾端位元;
     * 但本型別不強制該條件——{@code bitSize} 也用於承載 client 回報的參數,
     * 而 §11.4 的長度公式與「尾端位元必須為 0」是對任意 m 都成立的通則。
     */
    public int byteLength() {
        return Math.toIntExact((bitSize + 7) / 8);
    }

    /** L4:三者皆相同才相容;capacity 與 fpr 只是推導輸入,不影響 client 的比對能力。 */
    public boolean isCompatibleWith(BloomParameters other) {
        return other != null
                && algorithm == other.algorithm
                && hashFunctionCount == other.hashFunctionCount
                && bitSize == other.bitSize;
    }
}
