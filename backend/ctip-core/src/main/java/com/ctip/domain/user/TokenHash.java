package com.ctip.domain.user;

import com.ctip.domain.shared.Sha256Digest;
import java.util.regex.Pattern;

/**
 * Refresh token 的 SHA-256 雜湊(hex 小寫 64 碼)。不變量 U3 的姊妹規則:
 * refresh token 原文絕不進入持久化(docs/spec/10-identity-plans.md §10.4)。
 */
public record TokenHash(String value) {

    private static final Pattern HEX_64 = Pattern.compile("^[0-9a-f]{64}$");

    public TokenHash {
        if (value == null || !HEX_64.matcher(value).matches()) {
            throw new IllegalArgumentException("tokenHash 必須為小寫 hex 64 碼");
        }
    }

    /** 由 token 原文計算雜湊;原文不得離開此呼叫。 */
    public static TokenHash of(String plaintext) {
        return new TokenHash(Sha256Digest.hex(plaintext));
    }
}
