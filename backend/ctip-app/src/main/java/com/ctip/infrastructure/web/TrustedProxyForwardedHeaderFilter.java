package com.ctip.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.filter.ForwardedHeaderFilter;

/**
 * {@code server.forward-headers-strategy=framework} 的 filter,加上「只信任指定來源的代理」
 * (docs/spec/10-identity-plans.md §10.7)。
 *
 * <p>Boot 內建的 {@link ForwardedHeaderFilter} 註冊<strong>無條件採信</strong>
 * {@code X-Forwarded-*}——應用只要有一條路徑能被直接連到,任何人都可以自稱來自任意 IP,
 * 限流的 IP 維度即失效。本類別以同型別的 bean 取代它({@code @ConditionalOnMissingFilterBean}
 * 會讓 Boot 的那個退讓),對不信任的對端<strong>不處理</strong>轉發標頭,
 * {@code getRemoteAddr()} 因此維持為直連對端。
 *
 * <p>「不處理」而非「刪除標頭」:本專案唯一的 client IP 來源是 {@code getRemoteAddr()}
 * ({@link ClientIp}),不採信即已足夠;真要刪除得包一層 request wrapper,
 * 反而多一個會與 Boot 版本綁死的自訂型別。
 */
public class TrustedProxyForwardedHeaderFilter extends ForwardedHeaderFilter {

    private final TrustedProxies trustedProxies;

    public TrustedProxyForwardedHeaderFilter(TrustedProxies trustedProxies) {
        this.trustedProxies = trustedProxies;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !trustedProxies.contains(request.getRemoteAddr()) || super.shouldNotFilter(request);
    }
}
