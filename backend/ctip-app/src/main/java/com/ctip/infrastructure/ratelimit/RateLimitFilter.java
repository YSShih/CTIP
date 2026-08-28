package com.ctip.infrastructure.ratelimit;

import com.ctip.application.port.ClockPort;
import com.ctip.application.port.RateLimitKey;
import com.ctip.application.port.RateLimitResult;
import com.ctip.application.port.RateLimiterPort;
import com.ctip.domain.plan.Plan;
import com.ctip.infrastructure.web.ClientIp;
import com.ctip.infrastructure.web.FilterErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.function.Supplier;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 限流套用點(docs/spec/10-identity-plans.md §10.7):單一 filter,禁止 Decorator 堆疊。
 * 維度 4(匿名 IP,minute + day,任一超限即 429);X-RateLimit-* 於所有回應帶上,
 * 反映當下最緊的維度;429 另帶 Retry-After。IPv6 取 /64 前綴。
 * /actuator 為基礎設施探針(容器 healthcheck),不套用。
 * 由 RateLimitConfig 以 @Bean 建立——infrastructure 不得反向依賴 config(ArchUnit 規則 5)。
 *
 * <p>配額值自 Phase 14 起讀 plans 表的 ANONYMOUS 方案(§10.7「Phase 14 移入 plans 表」),
 * 不再有 property 版本。本 filter 排在認證之前,因此只認得匿名身分;
 * 維度 1–3(apiKey / user / tenant)需要已解析的身分,屬 Phase 17,
 * <strong>屆時不得把維度 4 一起搬到認證之後</strong>(ADR 0012 決策 16)。
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiterPort limiter;
    private final Supplier<Plan> anonymousPlan;
    private final boolean enabled;
    private final ClockPort clock;
    private final FilterErrorWriter errorWriter;

    /**
     * @param anonymousPlan ANONYMOUS 方案的取得方式;傳 supplier 而非整個 QuotaService,
     *     是因為本 filter 只需要兩個數字,而它排在認證之前、對每個請求都跑
     */
    public RateLimitFilter(RateLimiterPort limiter, Supplier<Plan> anonymousPlan, boolean enabled, ClockPort clock) {
        this.limiter = limiter;
        this.anonymousPlan = anonymousPlan;
        this.enabled = enabled;
        this.clock = clock;
        this.errorWriter = new FilterErrorWriter(clock);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // getRequestURI() 為未正規化原文;含 ".." 的路徑不得享有 /actuator 豁免(路徑穿越防禦)
        String uri = request.getRequestURI();
        return !enabled || isCorsPreflight(request) || (uri.startsWith("/actuator") && !uri.contains(".."));
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

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String subject = normalizeIp(request.getRemoteAddr());
        Plan plan = anonymousPlan.get();
        // §10.7 依序檢查,任一超限即拒絕:minute 超限的請求不再消耗 day 配額,
        // 否則被 429 的猛打流量會燒光整個 IP 的日配額
        RateLimitResult minute = limiter.tryConsume(
                RateLimitKey.anonymousIp(subject, RateLimitKey.Window.MINUTE), 1, plan.requestsPerMinute());
        if (!minute.allowed()) {
            writeRateLimitHeaders(response, minute);
            reject(request, response, minute);
            return;
        }
        RateLimitResult day = limiter.tryConsume(
                RateLimitKey.anonymousIp(subject, RateLimitKey.Window.DAY), 1, plan.requestsPerDay());
        RateLimitResult tightest = minute.remaining() <= day.remaining() ? minute : day;
        writeRateLimitHeaders(response, tightest);
        if (!day.allowed()) {
            reject(request, response, day);
            return;
        }
        chain.doFilter(request, response);
    }

    private static void writeRateLimitHeaders(HttpServletResponse response, RateLimitResult result) {
        response.setHeader("X-RateLimit-Limit", RateLimitHeaders.value(result.limit()));
        response.setHeader("X-RateLimit-Remaining", RateLimitHeaders.remaining(result));
        response.setHeader("X-RateLimit-Reset", Long.toString(result.resetAt().getEpochSecond()));
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, RateLimitResult rejecting)
            throws IOException {
        long retryAfter =
                Math.max(1, Duration.between(clock.now(), rejecting.resetAt()).getSeconds());
        response.setHeader("Retry-After", Long.toString(retryAfter));
        errorWriter.write(request, response, 429, "RATE_LIMIT_EXCEEDED", "Rate limit exceeded");
    }

    /** 匿名 IP 正規化(§10.7);與同步節流共用同一份規則,見 {@link ClientIp}。 */
    static String normalizeIp(String remoteAddr) {
        return ClientIp.normalize(remoteAddr);
    }
}
