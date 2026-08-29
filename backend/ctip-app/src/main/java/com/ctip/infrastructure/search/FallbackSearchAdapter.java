package com.ctip.infrastructure.search;

import com.ctip.application.port.SearchPort;
import com.ctip.application.port.SearchQuery;
import com.ctip.application.port.SearchResult;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Elasticsearch 不可用時自動降級為 PostgreSQL 搜尋
 * (docs/spec/13-platform-ops.md §13.7:「回 200 並在回應 header 帶
 * {@code X-Search-Backend: postgres},不得回 500」;「降級邏輯以 Resilience4j circuit breaker 實作於
 * {@code SearchPort} 的組合實作 {@code FallbackSearchAdapter},<strong>不在 controller 判斷</strong>」)。
 *
 * <p>circuit breaker 是必要的而不只是好看:ES 掛掉時每一次查詢都要等連線逾時,
 * 沒有斷路器的話「降級成功」會伴隨每個請求數秒的延遲,等同於服務仍然不可用。
 * 開路後直接走 PostgreSQL,{@code waitDurationInOpenState} 到期再試探。
 *
 * <p>回傳值帶著 {@code SearchBackend},controller 只是把它寫進標頭——降級的判斷完全在這裡。
 */
public class FallbackSearchAdapter implements SearchPort {

    /** §13.7 未指定斷路器參數。取比 §8.5 的來源抓取(20 次窗口)更靈敏的值:使用者查詢等不起 20 次逾時。 */
    private static final int SLIDING_WINDOW = 10;

    private static final int MINIMUM_CALLS = 3;
    private static final float FAILURE_RATE_THRESHOLD = 50f;
    private static final java.time.Duration WAIT_IN_OPEN_STATE = java.time.Duration.ofSeconds(30);

    private static final Logger log = LoggerFactory.getLogger(FallbackSearchAdapter.class);

    private final SearchPort elasticsearch;
    private final SearchPort postgres;
    private final CircuitBreaker breaker;

    public FallbackSearchAdapter(SearchPort elasticsearch, SearchPort postgres) {
        this.elasticsearch = elasticsearch;
        this.postgres = postgres;
        this.breaker = CircuitBreaker.of("search", defaults());
    }

    static io.github.resilience4j.circuitbreaker.CircuitBreakerConfig defaults() {
        return io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                .slidingWindowType(
                        io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(SLIDING_WINDOW)
                .minimumNumberOfCalls(MINIMUM_CALLS)
                .failureRateThreshold(FAILURE_RATE_THRESHOLD)
                .waitDurationInOpenState(WAIT_IN_OPEN_STATE)
                .build();
    }

    @Override
    public SearchResult search(SearchQuery query) {
        try {
            return breaker.executeSupplier(() -> elasticsearch.search(query));
        } catch (CallNotPermittedException e) {
            log.debug("搜尋斷路器開路中,直接使用 PostgreSQL");
        } catch (RuntimeException e) {
            log.warn("Elasticsearch 搜尋失敗,降級為 PostgreSQL(§13.7)", e);
        }
        return postgres.search(query);
    }

    /** 供測試與診斷觀察斷路器狀態。 */
    public CircuitBreaker.State circuitBreakerState() {
        return breaker.getState();
    }
}
