package com.ctip.domain.source;

import java.util.regex.Pattern;

/**
 * 錯誤訊息憑證遮罩(不變量 S5:last_error_message 不得包含憑證,寫入前必須經過遮罩)。
 * 保守遮罩常見的「鍵=值」憑證樣式與 Bearer token;呼叫端仍應優先避免把憑證放進錯誤訊息。
 */
final class CredentialMasker {

    private static final Pattern KEY_VALUE = Pattern.compile(
            "(?i)(password|passwd|pwd|secret|token|api[-_]?key|authorization|credential)(\\s*[=:]\\s*)\\S+");
    private static final Pattern BEARER = Pattern.compile("(?i)bearer\\s+\\S+");

    private CredentialMasker() {}

    static String mask(String message) {
        if (message == null) {
            return null;
        }
        // 先遮 Bearer token,避免 "Authorization: Bearer x" 被 KEY_VALUE 只吃掉 "Bearer" 而洩漏 token
        String masked = BEARER.matcher(message).replaceAll("Bearer ***");
        return KEY_VALUE.matcher(masked).replaceAll("$1$2***");
    }
}
