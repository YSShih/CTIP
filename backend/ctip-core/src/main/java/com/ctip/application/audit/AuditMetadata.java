package com.ctip.application.audit;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * {@code audit_logs.metadata} 的清洗(docs/spec/13-platform-ops.md §13.5 規則 5:
 * <strong>絕不含</strong>憑證、token 原文、密碼、完整 {@code Authorization} 標頭)。
 *
 * <p>做成一道**過濾**而不是一條註解:稽核的呼叫端散落在 filter、service 與事件 listener,
 * 任何一處手滑把整個請求標頭塞進 metadata,就會讓一張永不更新的表裡留下憑證。
 * 命中禁用鍵一律以 {@value #REDACTED} 取代(而不是丟例外)——稽核不得使業務操作失敗。
 */
public final class AuditMetadata {

    public static final String REDACTED = "[redacted]";

    private static final int VALUE_MAX_LENGTH = 512;

    /** 鍵名只要**包含**這些片段(不分大小寫)即遮蔽。 */
    private static final String[] FORBIDDEN_FRAGMENTS = {
        "password", "token", "secret", "credential", "authorization", "apikey", "api-key", "api_key", "cookie"
    };

    private AuditMetadata() {}

    public static Map<String, Object> sanitize(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> clean = new LinkedHashMap<>();
        raw.forEach((key, value) -> clean.put(key, isForbidden(key) ? REDACTED : truncate(value)));
        return Map.copyOf(clean);
    }

    private static boolean isForbidden(String key) {
        String lower = key == null ? "" : key.toLowerCase(Locale.ROOT);
        for (String fragment : FORBIDDEN_FRAGMENTS) {
            if (lower.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    private static Object truncate(Object value) {
        if (value instanceof String text && text.length() > VALUE_MAX_LENGTH) {
            return text.substring(0, VALUE_MAX_LENGTH);
        }
        return value;
    }
}
