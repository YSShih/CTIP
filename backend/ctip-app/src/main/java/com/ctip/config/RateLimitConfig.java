package com.ctip.config;

import com.ctip.application.plan.QuotaService;
import com.ctip.application.port.ClockPort;
import com.ctip.application.port.RateLimiterPort;
import com.ctip.domain.plan.PlanCode;
import com.ctip.infrastructure.ratelimit.InMemoryRateLimiter;
import com.ctip.infrastructure.ratelimit.RateLimitFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 限流後端裝配(docs/spec/10-identity-plans.md §10.7)。
 * M1 只有記憶體實作;RATE_LIMIT_BACKEND=redis 在 Phase 17 前暫以記憶體實作代替並 WARN
 * (限流仍然生效;單一實例下語意等價,見 ADR 0004)。
 */
@Configuration(proxyBeanMethods = false)
public class RateLimitConfig {

    private static final Logger log = LoggerFactory.getLogger(RateLimitConfig.class);

    @Bean
    RateLimiterPort rateLimiterPort(CtipProperties properties, ClockPort clock) {
        CtipProperties.RateLimit rateLimit = properties.rateLimit();
        if (rateLimit.backend() == CtipProperties.RateLimit.Backend.REDIS) {
            log.warn("RATE_LIMIT_BACKEND=redis:RedisRateLimiter 於 Phase 17(M2)提供,暫以記憶體實作代替(僅單一實例正確)");
        }
        return new InMemoryRateLimiter(clock);
    }

    /**
     * 限流必須排在 Spring Security filter chain <strong>之前</strong>。
     *
     * <p>認證 filter 在憑證無效時會直接寫出 401 並中止 chain,排在 chain 之後的限流器根本不會執行——
     * 只要掛一個亂寫的 {@code Authorization} 標頭就能無限量發送請求(實測:75 次無效 token 全回 401、
     * 零個 429,而同 IP 的匿名請求 60 次後正常 429)。每一次嘗試都會查一次資料庫,
     * 這同時是暴力破解與資源耗盡的入口。Boot 對 Filter bean 的預設順序是 LOWEST_PRECEDENCE,
     * 故此處以 FilterRegistrationBean 明確排在 security chain(SecurityFilterProperties.DEFAULT_FILTER_ORDER = -100)之前。
     *
     * <p>Phase 17 加入 key/user/tenant 維度時,那些維度需要已解析的身分、只能在認證之後檢查;
     * 但 <strong>IP 維度必須留在認證之前</strong>,否則這個繞過會回來。
     */
    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimitFilter(
            RateLimiterPort limiter, QuotaService quotas, CtipProperties properties, ClockPort clock) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(new RateLimitFilter(
                limiter,
                () -> quotas.byCode(PlanCode.ANONYMOUS),
                properties.rateLimit().enabled(),
                clock));
        registration.setOrder(SecurityFilterProperties.DEFAULT_FILTER_ORDER - 1);
        return registration;
    }
}
