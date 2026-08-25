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
        return !enabled || request.getRequestURI().startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String subject = normalizeIp(request.getRemoteAddr());
        RateLimitResult minute = limiter.tryConsume(RateLimitKey.anonymousIp(subject, RateLimitKey.Window.MINUTE), 1);
        RateLimitResult day = limiter.tryConsume(RateLimitKey.anonymousIp(subject, RateLimitKey.Window.DAY), 1);

        RateLimitResult tightest = minute.remaining() <= day.remaining() ? minute : day;
        response.setHeader("X-RateLimit-Limit", Long.toString(tightest.limit()));
        response.setHeader("X-RateLimit-Remaining", Long.toString(tightest.remaining()));
        response.setHeader("X-RateLimit-Reset", Long.toString(tightest.resetAt().getEpochSecond()));

        if (minute.allowed() && day.allowed()) {
            chain.doFilter(request, response);
            return;
        }
        RateLimitResult rejecting = minute.allowed() ? day : minute;
        long retryAfter =
                Math.max(1, Duration.between(clock.now(), rejecting.resetAt()).getSeconds());
        response.setStatus(429);
        response.setHeader("Retry-After", Long.toString(retryAfter));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"rate_limit_exceeded\",\"retryAfterSeconds\":" + retryAfter + "}");
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
