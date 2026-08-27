package com.ctip.domain.user;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 電子郵件位址。不變量 U1:一律以小寫儲存(全域唯一由 DB 唯一約束強制)。
 * 格式檢查刻意保守(RFC 5322 完整文法在此無實益):必須有 local@domain 且網域含點。
 */
public record EmailAddress(String value) {

    private static final Pattern FORMAT = Pattern.compile("^[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+$");
    private static final int MAX_LENGTH = 320;

    public EmailAddress {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("email 不得為空");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("email 長度不得超過 " + MAX_LENGTH);
        }
        if (!value.equals(value.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("email 必須以小寫儲存(不變量 U1):" + value);
        }
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("email 格式不符:" + value);
        }
    }

    /** 由使用者輸入建立:先正規化為小寫並去除前後空白,再驗證。 */
    public static EmailAddress of(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("email 不得為空");
        }
        return new EmailAddress(raw.trim().toLowerCase(Locale.ROOT));
    }
}
