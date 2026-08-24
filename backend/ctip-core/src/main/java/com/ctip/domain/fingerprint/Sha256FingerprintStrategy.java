package com.ctip.domain.fingerprint;

import com.ctip.sdk.FingerprintAlgorithm;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 指紋輸入為 normalizedValue 的 UTF-8 位元組,輸出 64 字元小寫十六進位(docs/spec/07-domain-intel.md §7.4)。 */
public final class Sha256FingerprintStrategy implements FingerprintStrategy {

    @Override
    public FingerprintAlgorithm algorithm() {
        return FingerprintAlgorithm.SHA256;
    }

    @Override
    public Fingerprint fingerprint(String canonicalValue) {
        byte[] digest = digestFor("SHA-256").digest(canonicalValue.getBytes(StandardCharsets.UTF_8));
        return new Fingerprint(HexFormat.of().formatHex(digest));
    }

    /** package-private 供測試驗證例外轉譯(JVM 保證內建 SHA-256,主路徑到不了 catch)。 */
    static MessageDigest digestFor(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 缺少 " + algorithm + " 實作", e);
        }
    }
}
