package com.ctip.domain.shared;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 → 小寫 hex 64 碼。憑證雜湊(refresh token、API key)共用的純函式
 * (docs/spec/10-identity-plans.md §10.4、§10.5:資料庫只存雜湊,絕不存原文)。
 */
public final class Sha256Digest {

    private Sha256Digest() {}

    public static String hex(String plaintext) {
        byte[] digest = digest().digest(plaintext.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 缺少 SHA-256 實作", e);
        }
    }
}
