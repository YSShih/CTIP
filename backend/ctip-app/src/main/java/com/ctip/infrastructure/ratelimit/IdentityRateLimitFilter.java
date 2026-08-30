package com.ctip.infrastructure.ratelimit;

import com.ctip.application.identity.AuthenticatedIdentity;
import com.ctip.application.plan.QuotaService;
import com.ctip.application.port.RateLimitKey;
import com.ctip.application.port.RateLimitResult;
import com.ctip.application.port.RateLimiterPort;
import com.ctip.domain.plan.EndpointClass;
import com.ctip.domain.plan.Plan;
import com.ctip.domain.plan.PlanCode;
import com.ctip.domain.plan.QuotaLimit;
import com.ctip.infrastructure.security.TenantContext;
import com.ctip.infrastructure.web.ClientIp;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 限流的<strong>第二個</strong>檢查點:維度 1–3 與維度 5,排在認證之後
 * (docs/spec/10-identity-plans.md §10.7;維度 4 在 {@link RateLimitFilter},認證之前)。
 *
 * <p>順序即 §10.7 的「由最 specific 到最 general」:
 * apiKey → user → tenant →(維度 4 已在前一個檢查點)→ endpointClass。
 * 每個維度先 minute 後 day,任一超限即 429 且不再消耗後續配額。
 *
 * <p>限額一律依<strong>呼叫者租戶的方案</strong>查表(§10.6「不得 hard-code」);
 * 維度 5 取該方案總配額的比例({@link EndpointClass})。匿名請求沒有維度 1–3,
 * 但仍有維度 5(以 IP 為主體、ANONYMOUS 方案的比例)。
 *
 * <p>本 filter 也負責歸還維度 4 的 token:見 {@link RateLimitFilter} 的說明。
 * 由 SecurityConfig 掛在認證 filter 之後——infrastructure 不得反向依賴 config(ArchUnit 規則 5)。
 */
public class IdentityRateLimitFilter extends OncePerRequestFilter {

    private final RateLimiterPort limiter;
    private final QuotaService quotas;
    private final TenantContext tenantContext;
    private final boolean enabled;
    private final RateLimitResponder responder;

    public IdentityRateLimitFilter(
            RateLimiterPort limiter,
            QuotaService quotas,
            TenantContext tenantContext,
            boolean enabled,
            RateLimitResponder responder) {
        this.limiter = limiter;
        this.quotas = quotas;
        this.tenantContext = tenantContext;
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
        Optional<AuthenticatedIdentity> identity = tenantContext.identity();
        if (identity.isPresent()) {
            refundAnonymousDimension(request);
        }
        Plan plan = quotas.planFor(tenantContext.tenantId());
        EndpointClass endpointClass = EndpointClassifier.classify(request.getMethod(), request.getRequestURI());
        for (RateLimitKey key : dimensions(identity, request, endpointClass)) {
            QuotaLimit minuteLimit = limitFor(plan, key, RateLimitKey.Window.MINUTE);
            RateLimitResult minute = limiter.tryConsume(key, 1, minuteLimit);
            responder.record(request, response, minute);
            if (!minute.allowed()) {
                responder.reject(request, response, minute, key);
                return;
            }
            RateLimitKey dayKey = key.inWindow(RateLimitKey.Window.DAY);
            RateLimitResult day = limiter.tryConsume(dayKey, 1, limitFor(plan, dayKey, RateLimitKey.Window.DAY));
            responder.record(request, response, day);
            if (!day.allowed()) {
                responder.reject(request, response, day, dayKey);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    /**
     * 維度 1–3(僅已認證)＋維度 5(一律),已依 §10.7 的 specific → general 排序。
     * 維度 5 的主體沿用最 specific 的那一個維度(匿名時是 IP),見 {@link RateLimitKey} 的說明。
     */
    private List<RateLimitKey> dimensions(
            Optional<AuthenticatedIdentity> identity, HttpServletRequest request, EndpointClass endpointClass) {
        List<RateLimitKey> keys = new ArrayList<>(4);
        RateLimitKey.Window minute = RateLimitKey.Window.MINUTE;
        identity.filter(AuthenticatedIdentity::isApiKey)
                .ifPresent(id -> keys.add(RateLimitKey.apiKey(id.apiKeyId(), minute)));
        identity.ifPresent(id -> keys.add(RateLimitKey.user(id.userId(), minute)));
        identity.ifPresent(id -> keys.add(RateLimitKey.tenant(id.tenantId(), minute)));
        RateLimitKey mostSpecific = keys.isEmpty()
                ? RateLimitKey.anonymousIp(ClientIp.normalize(request.getRemoteAddr()), minute)
                : keys.get(0);
        keys.add(mostSpecific.inClass(endpointClass));
        return keys;
    }

    private static QuotaLimit limitFor(Plan plan, RateLimitKey key, RateLimitKey.Window window) {
        QuotaLimit total = window == RateLimitKey.Window.MINUTE ? plan.requestsPerMinute() : plan.requestsPerDay();
        return key.endpointClass() == null ? total : key.endpointClass().shareOf(total);
    }

    /**
     * 認證成功即歸還維度 4 已扣的 token(§10.7 的維度 4 是「<strong>匿名</strong> IP」)。
     * 歸還後把「最緊維度」的紀錄一併清掉,否則標頭會停在匿名方案的數字上。
     */
    private void refundAnonymousDimension(HttpServletRequest request) {
        Plan anonymous = quotas.byCode(PlanCode.ANONYMOUS);
        String ip = ClientIp.normalize(request.getRemoteAddr());
        limiter.refund(RateLimitKey.anonymousIp(ip, RateLimitKey.Window.MINUTE), 1, anonymous.requestsPerMinute());
        limiter.refund(RateLimitKey.anonymousIp(ip, RateLimitKey.Window.DAY), 1, anonymous.requestsPerDay());
        responder.reset(request);
    }
}
