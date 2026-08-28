package com.ctip.infrastructure.ratelimit;

import com.ctip.application.port.RateLimitResult;
import com.ctip.domain.plan.QuotaLimit;

/**
 * {@code X-RateLimit-*} 的呈現(docs/spec/10-identity-plans.md §10.7:三個標頭在所有回應都必須帶上)。
 *
 * <p>ENTERPRISE 的 {@code requests_per_day} 是 {@code null}(依合約),而標頭必須有值——
 * 印 {@code -1} 或某個巨大數字都會被 client 當成真實配額。故以字面值 {@code unlimited} 表達
 * (ADR 0023);數值型的方案一律印數字,格式不變。
 */
public final class RateLimitHeaders {

    public static final String UNLIMITED = "unlimited";

    private RateLimitHeaders() {}

    public static String value(QuotaLimit limit) {
        return limit.isUnlimited() ? UNLIMITED : Long.toString(limit.orElse(0));
    }

    public static String remaining(RateLimitResult result) {
        return result.limit().isUnlimited() ? UNLIMITED : Long.toString(result.remaining());
    }
}
