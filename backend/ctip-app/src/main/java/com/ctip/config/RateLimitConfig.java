package com.ctip.config;

import com.ctip.application.plan.QuotaService;
import com.ctip.application.port.CachePort;
import com.ctip.application.port.ClockPort;
import com.ctip.application.port.RateLimiterPort;
import com.ctip.application.port.SyncThrottlePort;
import com.ctip.domain.plan.PlanCode;
import com.ctip.infrastructure.cache.InMemoryCache;
import com.ctip.infrastructure.ratelimit.CacheBackedSyncThrottle;
import com.ctip.infrastructure.ratelimit.IdentityRateLimitFilter;
import com.ctip.infrastructure.ratelimit.InMemoryRateLimiter;
import com.ctip.infrastructure.ratelimit.RateLimitFilter;
import com.ctip.infrastructure.ratelimit.RateLimitResponder;
import com.ctip.infrastructure.security.TenantContext;
import com.ctip.infrastructure.web.FilterErrorWriter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 限流與快取的裝配(docs/spec/10-identity-plans.md §10.7)。
 * {@code RATE_LIMIT_BACKEND=memory} 的實作在此;{@code =redis} 在 {@link RedisConfig}
 * ——兩者以同一個屬性互斥,不會同時存在兩個 {@link RateLimiterPort}。
 * 「{@code ENVIRONMENT != mvp} 卻用 memory」的 WARN 由 {@code StartupValidator} 發出(§5.7)。
 */
@Configuration(proxyBeanMethods = false)
public class RateLimitConfig {

    @Bean
    @ConditionalOnProperty(name = "ctip.rate-limit.backend", havingValue = "memory", matchIfMissing = true)
    RateLimiterPort rateLimiterPort(ClockPort clock) {
        return new InMemoryRateLimiter(clock);
    }

    @Bean
    @ConditionalOnProperty(name = "ctip.rate-limit.backend", havingValue = "memory", matchIfMissing = true)
    CachePort cachePort(ClockPort clock) {
        return new InMemoryCache(clock);
    }

    /**
     * 同步節流(11 §11.6 的 {@code min_sync_interval_seconds})。
     * 實作只有一個:狀態就是一筆帶 TTL 的字串,後端由 {@link CachePort} 決定
     * ——Redis 時即 Phase 16 交接單寫的 {@code SETEX}。
     */
    @Bean
    SyncThrottlePort syncThrottlePort(CachePort cache) {
        return new CacheBackedSyncThrottle(cache);
    }

    @Bean
    RateLimitResponder rateLimitResponder(ClockPort clock, FilterErrorWriter errorWriter) {
        return new RateLimitResponder(clock, errorWriter);
    }

    /**
     * 檢查點一(維度 4,匿名 IP)必須排在 Spring Security filter chain <strong>之前</strong>。
     *
     * <p>認證 filter 在憑證無效時會直接寫出 401 並中止 chain,排在 chain 之後的限流器根本不會執行——
     * 只要掛一個亂寫的 {@code Authorization} 標頭就能無限量發送請求(實測:75 次無效 token 全回 401、
     * 零個 429,而同 IP 的匿名請求 60 次後正常 429)。每一次嘗試都會查一次資料庫,
     * 這同時是暴力破解與資源耗盡的入口。Boot 對 Filter bean 的預設順序是 LOWEST_PRECEDENCE,
     * 故此處以 FilterRegistrationBean 明確排在 security chain
     * (SecurityFilterProperties.DEFAULT_FILTER_ORDER = -100)之前。
     *
     * <p>檢查點二(維度 1–3、5)在 {@link SecurityConfig},掛在認證 filter 之後——那些維度需要
     * 已解析的身分。<strong>維度 4 不得一起搬到認證之後</strong>(ADR 0012 決策 16)。
     */
    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimitFilter(
            RateLimiterPort limiter, QuotaService quotas, CtipProperties properties, RateLimitResponder responder) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(new RateLimitFilter(
                limiter,
                () -> quotas.byCode(PlanCode.ANONYMOUS),
                properties.rateLimit().enabled(),
                responder));
        registration.setOrder(SecurityFilterProperties.DEFAULT_FILTER_ORDER - 1);
        return registration;
    }

    @Bean
    IdentityRateLimitFilter identityRateLimitFilter(
            RateLimiterPort limiter,
            QuotaService quotas,
            TenantContext tenantContext,
            CtipProperties properties,
            RateLimitResponder responder) {
        return new IdentityRateLimitFilter(
                limiter, quotas, tenantContext, properties.rateLimit().enabled(), responder);
    }

    /**
     * 阻止 Boot 把上面那個 Filter bean 再自動註冊到 servlet chain 一次——
     * 它只能經 security chain 執行(認證之後),否則會在認證之前先跑一遍,拿不到身分。
     */
    @Bean
    FilterRegistrationBean<IdentityRateLimitFilter> identityRateLimitFilterRegistration(
            IdentityRateLimitFilter filter) {
        FilterRegistrationBean<IdentityRateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
