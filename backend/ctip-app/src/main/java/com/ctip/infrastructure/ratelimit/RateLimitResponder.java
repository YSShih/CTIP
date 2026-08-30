package com.ctip.infrastructure.ratelimit;

import com.ctip.application.port.ClockPort;
import com.ctip.application.port.RateLimitKey;
import com.ctip.application.port.RateLimitResult;
import com.ctip.infrastructure.observability.RateLimitMetrics;
import com.ctip.infrastructure.web.FilterErrorWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;

/**
 * 限流的回應面(docs/spec/10-identity-plans.md §10.7):{@code X-RateLimit-*} 三個標頭
 * 在<strong>所有</strong>回應都要帶上且「反映當下最緊的維度」,超限另加 {@code Retry-After}。
 *
 * <p>兩個檢查點({@link RateLimitFilter} 的維度 4 在認證之前、{@link IdentityRateLimitFilter}
 * 的維度 1–3／5 在認證之後)必須寫出同一組標頭,「最緊」也要跨兩者比較——
 * 因此把它收在這裡一處,而不是各寫一份(§10.7「集中一處,可讀可除錯」)。
 */
public class RateLimitResponder {

    private static final String TIGHTEST = RateLimitResponder.class.getName() + ".tightest";

    private final ClockPort clock;
    private final FilterErrorWriter errorWriter;
    private final RateLimitMetrics metrics;

    public RateLimitResponder(ClockPort clock, FilterErrorWriter errorWriter, RateLimitMetrics metrics) {
        this.clock = clock;
        this.errorWriter = errorWriter;
        this.metrics = metrics;
    }

    /** 記錄一個維度的判定;比目前最緊的還緊才改寫標頭。 */
    public void record(HttpServletRequest request, HttpServletResponse response, RateLimitResult result) {
        Object current = request.getAttribute(TIGHTEST);
        if (current instanceof RateLimitResult tightest && tightest.remaining() <= result.remaining()) {
            return;
        }
        request.setAttribute(TIGHTEST, result);
        response.setHeader("X-RateLimit-Limit", RateLimitHeaders.value(result.limit()));
        response.setHeader("X-RateLimit-Remaining", RateLimitHeaders.remaining(result));
        response.setHeader("X-RateLimit-Reset", Long.toString(result.resetAt().getEpochSecond()));
    }

    /**
     * 忘掉先前記錄的最緊維度。
     *
     * <p>唯一的呼叫端是認證成功之後:維度 4 的判定是以<strong>匿名</strong>方案的數字做的,
     * 那個數字對已認證的呼叫者沒有意義(而且它的 token 已經歸還),留著會讓
     * ENTERPRISE 的 client 看到 {@code X-RateLimit-Limit: 60}。
     */
    public void reset(HttpServletRequest request) {
        request.removeAttribute(TIGHTEST);
    }

    /**
     * 429 的唯一出口,因此也是 {@code ctip.ratelimit.rejected{dimension}}(13 §13.6)的唯一計數點——
     * 兩個檢查點各自計數會漏掉將來新增的第三個。
     */
    public void reject(
            HttpServletRequest request, HttpServletResponse response, RateLimitResult rejecting, RateLimitKey key)
            throws IOException {
        metrics.rejected(key);
        long retryAfter =
                Math.max(1, Duration.between(clock.now(), rejecting.resetAt()).getSeconds());
        response.setHeader("Retry-After", Long.toString(retryAfter));
        errorWriter.write(request, response, 429, "RATE_LIMIT_EXCEEDED", "Rate limit exceeded");
    }
}
