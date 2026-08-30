package com.ctip.infrastructure.ratelimit;

import com.ctip.domain.plan.EndpointClass;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 限流維度 5 的端點分類(docs/spec/10-identity-plans.md §10.7:
 * {@code read}(GET/查詢)、{@code write}(POST/PATCH/DELETE)、
 * {@code heavy}(bloom 下載、STIX bundle、import))。
 *
 * <p>{@code POST /iocs/search} 與 {@code POST /iocs/lookup} 歸 <strong>read</strong>:
 * §10.7 的 read 明文含「查詢」,而這兩支是查詢端點(§9.1 需要的是 {@code ioc:read})。
 * 照 HTTP 方法字面歸成 write 會把前端唯一的搜尋路徑壓到總配額的 20%。
 *
 * <p><strong>比對前必須正規化路徑。</strong>限流 filter 排在 DispatcherServlet 之前,
 * 拿得到的只有 {@code getRequestURI()} 的<strong>原文</strong>;而 Spring 的 {@code PathPattern}
 * 是拿<em>解碼後、去除路徑參數</em>的段落去 routing 的。兩者不一致時,
 * {@code /api/v1/iocs/%69mport} 或 {@code /api/v1/iocs/import;x=1} 會照樣打到 import handler,
 * 卻被這裡歸成 {@code write}——heavy 的 5% 上限就被換成 write 的 20%,那正是最貴的三個端點。
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
        String normalized = normalize(path);
        if (HEAVY_PATHS.contains(normalized)) {
            return EndpointClass.HEAVY;
        }
        if (READ_METHODS.contains(method) || QUERY_POST_PATHS.contains(normalized)) {
            return EndpointClass.READ;
        }
        return EndpointClass.WRITE;
    }

    /**
     * 原始 request URI → routing 實際看到的路徑:逐段去除 {@code ;} 之後的路徑參數、
     * 逐段百分比解碼(解碼後的 {@code /} 不再視為分隔符,與 {@code RequestPath} 一致)、
     * 去除尾斜線。無法解碼的輸入原樣回傳——那種請求不會 route 到任何 handler。
     */
    public static String normalize(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        String decoded;
        try {
            decoded = Stream.of(path.split("/", -1))
                    .map(EndpointClassifier::decodeSegment)
                    .collect(Collectors.joining("/"));
        } catch (IllegalArgumentException e) {
            return path;
        }
        return withoutTrailingSlash(decoded);
    }

    private static String decodeSegment(String segment) {
        int semicolon = segment.indexOf(';');
        String withoutParameters = semicolon < 0 ? segment : segment.substring(0, semicolon);
        // '+' 在路徑段裡是字面加號,不是空白;URLDecoder 走的是 form 語意,故先保護起來
        String decoded = URLDecoder.decode(withoutParameters.replace("+", "%2B"), StandardCharsets.UTF_8);
        // 解出來的 '/' 不得變成段落分隔符(Spring 的 RequestPath 也不會),否則 %2F 就能拼出任意分類
        return decoded.replace("/", "%2F");
    }

    private static String withoutTrailingSlash(String path) {
        return path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }
}
