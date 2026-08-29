package com.ctip.infrastructure.ratelimit;

import com.ctip.application.port.RateLimitKey;
import com.ctip.application.port.RateLimitResult;
import com.ctip.application.port.RateLimiterPort;
import com.ctip.domain.plan.Plan;
import com.ctip.infrastructure.web.ClientIp;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.function.Supplier;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 限流的<strong>第一個</strong>檢查點:維度 4(匿名 IP),排在認證之前
 * (docs/spec/10-identity-plans.md §10.7)。IPv6 取 /64 前綴。
 *
 * <p>為什麼一定要在認證之前:認證 filter 在憑證無效時直接寫出 401 並中止 chain,
 * 排在其後的限流器根本不會執行——只要掛一個亂寫的 {@code Authorization} 標頭就能無限量發送
 * (實測:75 次無效 token 全回 401、零個 429)。每次嘗試都查一次資料庫,
 * 那同時是暴力破解與資源耗盡的入口(ADR 0012 決策 16)。
 *
 * <p>維度 1–3(apiKey / user / tenant)與維度 5 需要已解析的身分,在
 * {@link IdentityRateLimitFilter}(認證之後)。<strong>不得把維度 4 一起搬到認證之後。</strong>
 *
 * <p>本檢查點對「帶憑證但尚未驗證」的請求也扣 token;認證成功後由
 * {@link IdentityRateLimitFilter} 歸還——維度 4 的語意是「<strong>匿名</strong> IP」,
 * 若不歸還,ENTERPRISE 的 client 會被匿名方案的 60/min 綁死(ADR 0026)。
 * 歸還發生在 controller 之前,因此對已認證流量,維度 4 實際上是
 * 「同一 IP 同時進行中的請求數」上限而非速率上限;認證失敗者沒有歸還的機會,暴力破解仍被擋。
 *
 * <p>由 RateLimitConfig 以 @Bean 建立——infrastructure 不得反向依賴 config(ArchUnit 規則 5)。
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiterPort limiter;
    private final Supplier<Plan> anonymousPlan;
    private final boolean enabled;
    private final RateLimitResponder responder;

    /**
     * @param anonymousPlan ANONYMOUS 方案的取得方式;傳 supplier 而非整個 QuotaService,
     *     是因為本 filter 只需要兩個數字,而它排在認證之前、對每個請求都跑
     */
    public RateLimitFilter(
            RateLimiterPort limiter, Supplier<Plan> anonymousPlan, boolean enabled, RateLimitResponder responder) {
        this.limiter = limiter;
        this.anonymousPlan = anonymousPlan;
        this.enabled = enabled;
        this.responder = responder;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled || RateLimitScope.exempt(request);
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
        responder.record(request, response, minute);
        if (!minute.allowed()) {
            responder.reject(request, response, minute);
            return;
        }
        RateLimitResult day = limiter.tryConsume(
                RateLimitKey.anonymousIp(subject, RateLimitKey.Window.DAY), 1, plan.requestsPerDay());
        responder.record(request, response, day);
        if (!day.allowed()) {
            responder.reject(request, response, day);
            return;
        }
        chain.doFilter(request, response);
    }

    /** 匿名 IP 正規化(§10.7);與同步節流共用同一份規則,見 {@link ClientIp}。 */
    static String normalizeIp(String remoteAddr) {
        return ClientIp.normalize(remoteAddr);
    }
}
