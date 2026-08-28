package com.ctip.application.port;

import com.ctip.domain.plan.QuotaLimit;

/**
 * 限流(docs/spec/10-identity-plans.md §10.7)。
 * M1:InMemoryRateLimiter(Bucket4j,僅單一實例正確;Phase 6);
 * M2:RedisRateLimiter(Phase 17)。所有回應都必須帶 X-RateLimit-* 標頭,超限回 429。
 *
 * <p>限額<strong>隨呼叫傳入</strong>(Phase 14;ADR 0019):§10.6 的 60/300/1200/6000 是
 * 依方案查表的 per-key 值,實作端無從得知;{@link QuotaLimit} 同時承載「無上限」
 * (ENTERPRISE 的 requests_per_day 為 null)。
 */
public interface RateLimiterPort {

    RateLimitResult tryConsume(RateLimitKey key, int tokens, QuotaLimit limit);

    /** 不消耗任何 token,只回報目前餘額(用量查詢端點)。 */
    RateLimitResult peek(RateLimitKey key, QuotaLimit limit);
}
