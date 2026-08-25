package com.ctip.application.port;

/**
 * 限流(docs/spec/10-identity-plans.md §10.7)。
 * M1:InMemoryRateLimiter(Bucket4j,僅單一實例正確;Phase 6);
 * M2:RedisRateLimiter(Phase 17)。所有回應都必須帶 X-RateLimit-* 標頭,超限回 429。
 */
public interface RateLimiterPort {

    RateLimitResult tryConsume(RateLimitKey key, int tokens);
}
