package com.ctip.infrastructure.ratelimit;

import com.ctip.domain.plan.EndpointClass;
import java.util.Set;

/**
 * 限流維度 5 的端點分類(docs/spec/10-identity-plans.md §10.7:
 * {@code read}(GET/查詢)、{@code write}(POST/PATCH/DELETE)、
 * {@code heavy}(bloom 下載、STIX bundle、import))。
 *
 * <p>{@code POST /iocs/search} 與 {@code POST /iocs/lookup} 歸 <strong>read</strong>:
 * §10.7 的 read 明文含「查詢」,而這兩支是查詢端點(§9.1 需要的是 {@code ioc:read})。
 * 照 HTTP 方法字面歸成 write 會把前端唯一的搜尋路徑壓到總配額的 20%。
 */
public final class EndpointClassifier {

    /** 單次成本高出數個量級的端點;{@code heavy} 優先於方法判定。 */
    private static final Set<String> HEAVY_PATHS =
            Set.of("/api/v1/sync/bloom", "/api/v1/stix/bundle", "/api/v1/iocs/import");

    /** 以 POST 表達的查詢(請求體裝的是查詢條件,不改變任何狀態)。 */
    private static final Set<String> QUERY_POST_PATHS = Set.of("/api/v1/iocs/search", "/api/v1/iocs/lookup");

    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private EndpointClassifier() {}

    public static EndpointClass classify(String method, String path) {
        String normalized = withoutTrailingSlash(path);
        if (HEAVY_PATHS.contains(normalized)) {
            return EndpointClass.HEAVY;
        }
        if (READ_METHODS.contains(method) || QUERY_POST_PATHS.contains(normalized)) {
            return EndpointClass.READ;
        }
        return EndpointClass.WRITE;
    }

    private static String withoutTrailingSlash(String path) {
        return path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }
}
