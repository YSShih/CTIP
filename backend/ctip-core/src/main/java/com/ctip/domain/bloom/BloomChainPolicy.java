package com.ctip.domain.bloom;

/**
 * 強制重下 full snapshot 的門檻(docs/spec/11-sync-bloom.md §11.3)。
 *
 * <p>{@code maxDeltaChain} 由 {@code BLOOM_MAX_DELTA_CHAIN} 設定(預設 24);
 * 累計 delta 大小超過 full 的 30% 是規格固定值。
 *
 * <p>把兩者收進值物件,是為了讓 {@link BloomVersion#requiresFullSnapshot} 不必接受設定物件,
 * 也不必把 24 這個數字寫死在 domain(02 §2.3 列出的簽章為兩個參數,此處多一個 policy,見 ADR 0024)。
 */
public record BloomChainPolicy(int maxDeltaChain, double maxCumulativeDeltaRatio) {

    private static final double DEFAULT_RATIO = 0.30;

    public BloomChainPolicy {
        if (maxDeltaChain < 1) {
            throw new IllegalArgumentException("maxDeltaChain 必須至少為 1:" + maxDeltaChain);
        }
        if (maxCumulativeDeltaRatio <= 0 || maxCumulativeDeltaRatio >= 1) {
            throw new IllegalArgumentException("maxCumulativeDeltaRatio 必須介於 0 與 1 之間:" + maxCumulativeDeltaRatio);
        }
    }

    public static BloomChainPolicy of(int maxDeltaChain) {
        return new BloomChainPolicy(maxDeltaChain, DEFAULT_RATIO);
    }
}
