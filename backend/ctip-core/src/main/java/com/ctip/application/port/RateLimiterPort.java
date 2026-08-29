package com.ctip.application.port;

import com.ctip.domain.plan.QuotaLimit;

/**
 * 限流(docs/spec/10-identity-plans.md §10.7)。
 * {@code RATE_LIMIT_BACKEND=memory} → InMemoryRateLimiter(Bucket4j,僅單一實例正確;Phase 6);
 * {@code =redis} → RedisRateLimiter(Bucket4j + bucket4j-redis;Phase 17)。
 * 所有回應都必須帶 X-RateLimit-* 標頭,超限回 429。
 *
 * <p>限額<strong>隨呼叫傳入</strong>(Phase 14;ADR 0019):§10.6 的 60/300/1200/6000 是
 * 依方案查表的 per-key 值,實作端無從得知;{@link QuotaLimit} 同時承載「無上限」
 * (ENTERPRISE 的 requests_per_day 為 null)。
 */
public interface RateLimiterPort {

    RateLimitResult tryConsume(RateLimitKey key, int tokens, QuotaLimit limit);

    /** 不消耗任何 token,只回報目前餘額(用量查詢端點)。 */
    RateLimitResult peek(RateLimitKey key, QuotaLimit limit);

    /**
     * 歸還先前消耗的 token(上限為桶容量,不會超額歸還)。
     *
     * <p>唯一的呼叫端是維度 4:限流必須排在認證<strong>之前</strong>(否則無效憑證完全繞過限流,
     * ADR 0012 決策 16),但那時還不知道請求會不會認證成功——而維度 4 是「<strong>匿名</strong> IP」,
     * 認證成功者該受的是自己方案的維度 1–3。因此先扣、認證成功後歸還(ADR 0026)。
     */
    void refund(RateLimitKey key, int tokens, QuotaLimit limit);
}
