package com.ctip.infrastructure.ratelimit;

import com.ctip.application.port.ClockPort;
import com.ctip.application.port.RateLimitKey;
import com.ctip.application.port.RateLimitResult;
import com.ctip.application.port.RateLimiterPort;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.HexFormat;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 限流套用點(docs/spec/10-identity-plans.md §10.7):單一 filter,禁止 Decorator 堆疊。
 * M1 為匿名 IP 維度(minute + day,任一超限即 429);X-RateLimit-* 於所有回應帶上,
 * 反映當下最緊的維度;429 另帶 Retry-After。IPv6 取 /64 前綴。
 * /actuator 為基礎設施探針(容器 healthcheck),不套用。
 * 由 RateLimitConfig 以 @Bean 建立——infrastructure 不得反向依賴 config(ArchUnit 規則 5)。
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiterPort limiter;
    private final boolean enabled;
    private final ClockPort clock;

    public RateLimitFilter(RateLimiterPort limiter, boolean enabled, ClockPort clock) {
        this.limiter = limiter;
        this.enabled = enabled;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // getRequestURI() 為未正規化原文;含 ".." 的路徑不得享有 /actuator 豁免(路徑穿越防禦)
        String uri = request.getRequestURI();
        return !enabled || (uri.startsWith("/actuator") && !uri.contains(".."));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String subject = normalizeIp(request.getRemoteAddr());
        // §10.7 依序檢查,任一超限即拒絕:minute 超限的請求不再消耗 day 配額,
        // 否則被 429 的猛打流量會燒光整個 IP 的日配額
        RateLimitResult minute = limiter.tryConsume(RateLimitKey.anonymousIp(subject, RateLimitKey.Window.MINUTE), 1);
        if (!minute.allowed()) {
            writeRateLimitHeaders(response, minute);
            reject(request, response, minute);
            return;
        }
        RateLimitResult day = limiter.tryConsume(RateLimitKey.anonymousIp(subject, RateLimitKey.Window.DAY), 1);
        RateLimitResult tightest = minute.remaining() <= day.remaining() ? minute : day;
        writeRateLimitHeaders(response, tightest);
        if (!day.allowed()) {
            reject(request, response, day);
            return;
        }
        chain.doFilter(request, response);
    }

    private static void writeRateLimitHeaders(HttpServletResponse response, RateLimitResult result) {
        response.setHeader("X-RateLimit-Limit", Long.toString(result.limit()));
        response.setHeader("X-RateLimit-Remaining", Long.toString(result.remaining()));
        response.setHeader("X-RateLimit-Reset", Long.toString(result.resetAt().getEpochSecond()));
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, RateLimitResult rejecting)
            throws IOException {
        long retryAfter =
                Math.max(1, Duration.between(clock.now(), rejecting.resetAt()).getSeconds());
        response.setStatus(429);
        response.setHeader("Retry-After", Long.toString(retryAfter));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        // 統一錯誤結構(09 §9.4);filter 在 MVC 之前,手工組 JSON;path 為 client 可控值,必須跳脫
        response.getWriter()
                .write("{\"timestamp\":\"" + clock.now() + "\",\"status\":429,\"code\":\"RATE_LIMIT_EXCEEDED\","
                        + "\"message\":\"Rate limit exceeded\",\"path\":\"" + escapeJson(request.getRequestURI())
                        + "\",\"traceId\":" + jsonStringOrNull(org.slf4j.MDC.get("traceId"))
                        + ",\"details\":[]}");
    }

    private static String jsonStringOrNull(String value) {
        return value == null ? "null" : "\"" + escapeJson(value) + "\"";
    }

    /** 最小 JSON 字串跳脫(引號、反斜線、控制字元)——不押注 servlet 容器對 request line 的過濾。 */
    static String escapeJson(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    /** 匿名 IP 正規化(§10.7):IPv4 取完整位址;IPv6 取 /64 前綴,避免以 /64 內位址繞過。 */
    static String normalizeIp(String remoteAddr) {
        try {
            InetAddress address = InetAddress.getByName(remoteAddr);
            byte[] bytes = address.getAddress();
            if (bytes.length == 4) {
                return address.getHostAddress();
            }
            return "v6-" + HexFormat.of().formatHex(bytes, 0, 8);
        } catch (UnknownHostException e) {
            return remoteAddr;
        }
    }
}
