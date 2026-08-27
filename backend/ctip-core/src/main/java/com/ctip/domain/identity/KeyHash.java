package com.ctip.domain.identity;

import com.ctip.domain.shared.Sha256Digest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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

    /**
     * 以常數時間比對呈交的完整 key。
     *
     * <p>{@code equals} 走 {@link String#equals},會在第一個相異字元短路;比對 secret 一律不用它。
     * 這裡的實際可利用性很低(攻擊者只控制原文,無法針對摘要前綴逐位元試探),
     * 但「比對憑證用常數時間」是不該有例外的基本衛生。
     */
    public boolean matches(String fullKey) {
        return MessageDigest.isEqual(
                value.getBytes(StandardCharsets.UTF_8),
                Sha256Digest.hex(fullKey).getBytes(StandardCharsets.UTF_8));
    }
}
