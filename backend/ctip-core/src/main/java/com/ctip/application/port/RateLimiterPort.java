package com.ctip.application.port;

/**
 * 限流(docs/spec/10-identity-plans.md)。M1 為記憶體實作(Phase 6),M2 換 Redis 後端;
 * key 為限流主體(如 tenant/IP + 端點),語意為「取得一次呼叫額度」。
 */
public interface RateLimiterPort {

    boolean tryAcquire(String key);
}
