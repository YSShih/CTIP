package com.ctip.domain.fingerprint;

import com.ctip.sdk.FingerprintAlgorithm;

/**
 * 指紋策略(docs/spec/07-domain-intel.md §7.4)。M1 只有 SHA-256 實作;
 * 介面保留是為了未來可換演算法(Bloom 需要多個獨立雜湊時),不是為了展示 Strategy Pattern。
 */
public interface FingerprintStrategy {

    FingerprintAlgorithm algorithm();

    Fingerprint fingerprint(String canonicalValue);
}
