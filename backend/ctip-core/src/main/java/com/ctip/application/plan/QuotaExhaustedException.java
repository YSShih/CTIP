package com.ctip.application.plan;

import com.ctip.application.port.RateLimitResult;

/**
 * 時間窗內的計數用罄(§9.7「配額超限的三種語意」)→ 429 RATE_LIMIT_EXCEEDED,
 * 帶 X-RateLimit-* 與 Retry-After。有重置時間,client 知道何時可再試。
 */
public class QuotaExhaustedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient RateLimitResult result;

    public QuotaExhaustedException(String message, RateLimitResult result) {
        super(message);
        this.result = result;
    }

    public RateLimitResult result() {
        return result;
    }
}
