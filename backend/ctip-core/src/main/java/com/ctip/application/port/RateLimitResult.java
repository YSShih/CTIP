package com.ctip.application.port;

import java.time.Instant;

/** 一次限流判定的結果(docs/spec/10-identity-plans.md §10.7);X-RateLimit-* 標頭的資料來源。 */
public record RateLimitResult(boolean allowed, long limit, long remaining, Instant resetAt) {}
