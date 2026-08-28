package com.ctip.domain.bloom;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Pattern;

/**
 * 未壓縮 artifact 位元組的 SHA-256,十六進位小寫(docs/spec/11-sync-bloom.md §11.4;不變量 L5)。
 *
 * <p>client 以此驗證下載或套用 delta 後的結果;不符即丟棄並重下 full。
 */
public record Checksum(String hex) {

    private static final Pattern FORMAT = Pattern.compile("^[0-9a-f]{64}$");
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    public Checksum {
        if (hex == null || !FORMAT.matcher(hex).matches()) {
            throw new IllegalArgumentException("checksum 必須為 64 字元小寫十六進位");
        }
    }

    public static Checksum sha256(byte[] content) {
        return new Checksum(toHex(digest(content)));
    }

    private static byte[] digest(byte[] content) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(content);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 未提供 SHA-256", e);
        }
    }

    private static String toHex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            out[i * 2] = HEX[(bytes[i] >> 4) & 0xF];
            out[i * 2 + 1] = HEX[bytes[i] & 0xF];
        }
        return new String(out);
    }

    /** 常數時間比對:checksum 不是機密,但比對結果會回給 client,沿用平台既有慣例。 */
    public boolean matches(Checksum other) {
        return other != null
                && MessageDigest.isEqual(
                        hex.getBytes(StandardCharsets.UTF_8), other.hex.getBytes(StandardCharsets.UTF_8));
    }
}
