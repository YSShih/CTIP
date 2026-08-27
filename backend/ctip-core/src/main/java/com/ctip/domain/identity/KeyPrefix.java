package com.ctip.domain.identity;

import java.util.regex.Pattern;

/**
 * API key 的明碼前綴,長度 8(不變量 K2,全域唯一由 DB 唯一約束強制)。
 *
 * <p>取自完整 key 的<strong>隨機段</strong>前 8 碼,而非整串的前 8 碼:完整格式為
 * {@code ctip_<env>_<32 base62>},整串前 8 碼恆為 {@code ctip_mvp} 之類的環境常數,
 * 與 {@code ux_api_keys_prefix} 唯一約束及 §10.5「以前綴定位單一列」直接衝突
 * (ADR 0012 決策 2)。
 */
public record KeyPrefix(String value) {

    static final int LENGTH = 8;
    private static final Pattern FORMAT = Pattern.compile("^[0-9A-Za-z]{8}$");

    public KeyPrefix {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("keyPrefix 必須為 8 碼 base62:" + value);
        }
    }
}
