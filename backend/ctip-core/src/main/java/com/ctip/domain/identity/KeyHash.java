package com.ctip.domain.identity;

import com.ctip.domain.shared.Sha256Digest;
import java.util.regex.Pattern;

/** 不變量 K1:資料庫只存 {@code SHA-256(fullKey)},原文僅於建立當下回傳一次。 */
public record KeyHash(String value) {

    private static final Pattern HEX_64 = Pattern.compile("^[0-9a-f]{64}$");

    public KeyHash {
        if (value == null || !HEX_64.matcher(value).matches()) {
            throw new IllegalArgumentException("keyHash 必須為小寫 hex 64 碼");
        }
    }

    public static KeyHash of(String fullKey) {
        return new KeyHash(Sha256Digest.hex(fullKey));
    }
}
