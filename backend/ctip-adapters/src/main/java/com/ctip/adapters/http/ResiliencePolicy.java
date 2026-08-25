package com.ctip.adapters.http;

import java.time.Duration;

/**
 * 韌性參數(docs/spec/08-ingestion-sdk.md §8.5)。{@link #defaults()} 為規格預設值;
 * 測試以縮短的間隔建構,避免真實等待。
 *
 * @param connectTimeout HTTP 連線逾時(預設 5s)
 * @param readTimeout HTTP 讀取逾時(預設 30s)
 * @param retryMaxAttempts 總嘗試次數 = 首次 + 3 次重試(預設 4)
 * @param retryInitialInterval 指數退避的首個間隔(預設 1s → 1s、2s、4s,加 jitter)
 * @param circuitBreakerFailureRateThreshold 失敗率門檻(預設 50%)
 * @param circuitBreakerSlidingWindowSize 計數滑動視窗(預設 20 次)
 * @param circuitBreakerWaitInOpenState 開啟後的等待(預設 60s)
 * @param bulkheadMaxConcurrentCalls 每來源並行抓取上限(預設 2)
 */
public record ResiliencePolicy(
        Duration connectTimeout,
        Duration readTimeout,
        int retryMaxAttempts,
        Duration retryInitialInterval,
        float circuitBreakerFailureRateThreshold,
        int circuitBreakerSlidingWindowSize,
        Duration circuitBreakerWaitInOpenState,
        int bulkheadMaxConcurrentCalls) {

    public static ResiliencePolicy defaults() {
        return new ResiliencePolicy(
                Duration.ofSeconds(5),
                Duration.ofSeconds(30),
                4,
                Duration.ofSeconds(1),
                50f,
                20,
                Duration.ofSeconds(60),
                2);
    }
}
