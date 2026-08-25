package com.ctip.adapters.http;

import com.ctip.sdk.ThreatSourceAdapter;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;

/**
 * Resilience4j 裝配的唯一集中點(docs/spec/08-ingestion-sdk.md §8.5):
 * retry(指數退避 + jitter)、circuit breaker、bulkhead 以組態方式套用於所有 adapter,
 * 不要求每個 adapter 自己加註解。實例以 sourceType 為 key——單一來源故障只開啟
 * 自己的 circuit breaker,不影響其他來源。
 */
public final class FetchResilience {

    private static final double BACKOFF_MULTIPLIER = 2.0;
    private static final double JITTER_FACTOR = 0.5;

    private final RetryRegistry retries;
    private final CircuitBreakerRegistry circuitBreakers;
    private final BulkheadRegistry bulkheads;

    public FetchResilience(ResiliencePolicy policy) {
        this.retries = RetryRegistry.of(RetryConfig.custom()
                .maxAttempts(policy.retryMaxAttempts())
                .intervalFunction(IntervalFunction.ofExponentialRandomBackoff(
                        policy.retryInitialInterval(), BACKOFF_MULTIPLIER, JITTER_FACTOR))
                .build());
        this.circuitBreakers = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .failureRateThreshold(policy.circuitBreakerFailureRateThreshold())
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(policy.circuitBreakerSlidingWindowSize())
                .minimumNumberOfCalls(policy.circuitBreakerSlidingWindowSize())
                .waitDurationInOpenState(policy.circuitBreakerWaitInOpenState())
                .build());
        this.bulkheads = BulkheadRegistry.of(BulkheadConfig.custom()
                .maxConcurrentCalls(policy.bulkheadMaxConcurrentCalls())
                .build());
    }

    /** 以來源為單位包上 retry / circuit breaker / bulkhead;sourceType 與 metadata 原樣委派。 */
    public ThreatSourceAdapter decorate(ThreatSourceAdapter adapter) {
        String key = adapter.sourceType().name();
        return new ResilientThreatSourceAdapter(
                adapter, retries.retry(key), circuitBreakers.circuitBreaker(key), bulkheads.bulkhead(key));
    }

    /** 觀測用:某來源的 circuit breaker 目前狀態(測試與未來的健康端點)。 */
    public CircuitBreaker.State circuitBreakerState(String sourceTypeName) {
        return circuitBreakers.circuitBreaker(sourceTypeName).getState();
    }
}
