package com.ctip.infrastructure.ratelimit;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 哪些請求不套用限流(docs/spec/10-identity-plans.md §10.7)。
 * 兩個檢查點必須用同一份規則,否則會出現「維度 4 放行、維度 5 擋下」這種不一致。
 */
final class RateLimitScope {

    private RateLimitScope() {}

    static boolean exempt(HttpServletRequest request) {
        return isActuator(request) || isCorsPreflight(request);
    }

    /**
     * {@code /actuator/*} 是 compose healthcheck 與探針路徑,限流會使容器永遠 unhealthy。
     * {@code getRequestURI()} 為未正規化原文;含 {@code ".."} 的路徑不得享有豁免(路徑穿越防禦)。
     */
    private static boolean isActuator(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/actuator") && !uri.contains("..");
    }

    /**
     * CORS preflight 不計入配額。
     *
     * <p>它是瀏覽器自動產生的額外往返——每個非簡單跨源請求都會多一次——計入等於把 SPA 的可用配額砍半。
     * 且 preflight 不帶憑證、不查資料庫,完全由 CORS 設定回答,沒有可被濫用的成本。
     * 判定條件刻意比「method == OPTIONS」窄:必須帶 {@code Access-Control-Request-Method},
     * 否則一般的 OPTIONS 就成了繞過限流的洞。
     */
    private static boolean isCorsPreflight(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
                && request.getHeader("Access-Control-Request-Method") != null;
    }
}
