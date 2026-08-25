package com.ctip.config;

import com.ctip.application.port.ClockPort;
import com.ctip.application.port.RateLimiterPort;
import com.ctip.infrastructure.ratelimit.InMemoryRateLimiter;
import com.ctip.infrastructure.ratelimit.RateLimitFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
        return new InMemoryRateLimiter(rateLimit.anonymousPerMinute(), rateLimit.anonymousPerDay(), clock);
    }

    @Bean
    RateLimitFilter rateLimitFilter(RateLimiterPort limiter, CtipProperties properties, ClockPort clock) {
        return new RateLimitFilter(limiter, properties.rateLimit().enabled(), clock);
    }
}
