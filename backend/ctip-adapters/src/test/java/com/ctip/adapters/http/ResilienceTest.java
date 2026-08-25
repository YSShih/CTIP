package com.ctip.adapters.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ctip.sdk.FetchContext;
import com.ctip.sdk.FetchResult;
import com.ctip.sdk.IocType;
import com.ctip.sdk.RedistributionPolicy;
import com.ctip.sdk.SourceMetadata;
import com.ctip.sdk.SourceType;
import com.ctip.sdk.ThreatSourceAdapter;
import com.ctip.sdk.Tlp;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * 韌性裝配(docs/spec/08-ingestion-sdk.md §8.5):retry 次數、circuit breaker 開啟、
 * bulkhead 並行上限、per-source 隔離、timeout 契約。測試以縮短的間隔建構,避免真實等待。
 */
@Tag("unit")
class ResilienceTest {

    private static final FetchContext CONTEXT = new FetchContext(null, null, Map.of(), 100);
    private static final FetchResult EMPTY = new FetchResult(List.of(), null, false);

    @Test
    void defaultsMatchSpec() {
        ResiliencePolicy defaults = ResiliencePolicy.defaults();
        assertThat(defaults.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(defaults.readTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(defaults.retryMaxAttempts()).isEqualTo(4); // 首次 + 3 次重試(1s、2s、4s + jitter)
        assertThat(defaults.retryInitialInterval()).isEqualTo(Duration.ofSeconds(1));
        assertThat(defaults.circuitBreakerFailureRateThreshold()).isEqualTo(50f);
        assertThat(defaults.circuitBreakerSlidingWindowSize()).isEqualTo(20);
        assertThat(defaults.circuitBreakerWaitInOpenState()).isEqualTo(Duration.ofSeconds(60));
        assertThat(defaults.bulkheadMaxConcurrentCalls()).isEqualTo(2);
    }

    @Test
    void retryRecoversAfterTransientFailures() {
        FlakyAdapter flaky = new FlakyAdapter(SourceType.MOCK_OPENPHISH, 2);
        ThreatSourceAdapter resilient = new FetchResilience(fastPolicy(4, 20, 2)).decorate(flaky);

        assertThat(resilient.fetch(CONTEXT)).isEqualTo(EMPTY);
        assertThat(flaky.calls.get()).isEqualTo(3); // 失敗 2 次 + 成功 1 次
    }

    @Test
    void retryGivesUpAfterMaxAttempts() {
        FlakyAdapter alwaysFailing = new FlakyAdapter(SourceType.MOCK_OPENPHISH, Integer.MAX_VALUE);
        ThreatSourceAdapter resilient = new FetchResilience(fastPolicy(4, 20, 2)).decorate(alwaysFailing);

        assertThatThrownBy(() -> resilient.fetch(CONTEXT)).isInstanceOf(IllegalStateException.class);
        assertThat(alwaysFailing.calls.get()).isEqualTo(4);
    }

    @Test
    void circuitBreakerOpensAndShortCircuits() {
        FlakyAdapter alwaysFailing = new FlakyAdapter(SourceType.MOCK_OPENPHISH, Integer.MAX_VALUE);
        FetchResilience resilience = new FetchResilience(fastPolicy(1, 4, 2));
        ThreatSourceAdapter resilient = resilience.decorate(alwaysFailing);

        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> resilient.fetch(CONTEXT)).isInstanceOf(IllegalStateException.class);
        }
        assertThat(resilience.circuitBreakerState(SourceType.MOCK_OPENPHISH.name()))
                .isEqualTo(CircuitBreaker.State.OPEN);

        int callsBefore = alwaysFailing.calls.get();
        assertThatThrownBy(() -> resilient.fetch(CONTEXT)).isInstanceOf(CallNotPermittedException.class);
        assertThat(alwaysFailing.calls.get()).isEqualTo(callsBefore); // 開啟後不再打到 adapter
    }

    @Test
    void failingSourceDoesNotOpenOtherSourcesBreaker() {
        FetchResilience resilience = new FetchResilience(fastPolicy(1, 4, 2));
        ThreatSourceAdapter failing =
                resilience.decorate(new FlakyAdapter(SourceType.MOCK_OPENPHISH, Integer.MAX_VALUE));
        ThreatSourceAdapter healthy = resilience.decorate(new FlakyAdapter(SourceType.MOCK_ABUSEIPDB, 0));

        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> failing.fetch(CONTEXT)).isInstanceOf(IllegalStateException.class);
        }
        assertThat(resilience.circuitBreakerState(SourceType.MOCK_OPENPHISH.name()))
                .isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(healthy.fetch(CONTEXT)).isEqualTo(EMPTY);
        assertThat(resilience.circuitBreakerState(SourceType.MOCK_ABUSEIPDB.name()))
                .isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void bulkheadRejectsThirdConcurrentFetch() throws Exception {
        BlockingAdapter blocking = new BlockingAdapter(SourceType.MOCK_ALIENVAULT);
        ThreatSourceAdapter resilient = new FetchResilience(fastPolicy(1, 20, 2)).decorate(blocking);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<FetchResult> first = executor.submit(() -> resilient.fetch(CONTEXT));
            Future<FetchResult> second = executor.submit(() -> resilient.fetch(CONTEXT));
            assertThat(blocking.entered.await(5, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> resilient.fetch(CONTEXT)).isInstanceOf(BulkheadFullException.class);

            blocking.release.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo(EMPTY);
            assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo(EMPTY);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void httpFeedClientsApplyTimeoutContract() {
        ResiliencePolicy defaults = ResiliencePolicy.defaults();
        HttpClient client = HttpFeedClients.newClient(defaults);
        HttpRequest request = HttpFeedClients.feedRequest(URI.create("https://feed.example.invalid/v1"), defaults)
                .build();

        assertThat(client.connectTimeout()).contains(Duration.ofSeconds(5));
        assertThat(request.timeout()).contains(Duration.ofSeconds(30));
        assertThat(request.method()).isEqualTo("GET");
    }

    /** retry 間隔 1ms 的測試用 policy,結構與預設一致。 */
    private static ResiliencePolicy fastPolicy(int retryMaxAttempts, int cbWindow, int bulkheadMax) {
        return new ResiliencePolicy(
                Duration.ofSeconds(5),
                Duration.ofSeconds(30),
                retryMaxAttempts,
                Duration.ofMillis(1),
                50f,
                cbWindow,
                Duration.ofSeconds(60),
                bulkheadMax);
    }

    /** 前 failuresBeforeSuccess 次丟例外,其後回空結果。 */
    private static final class FlakyAdapter implements ThreatSourceAdapter {
        private final SourceType sourceType;
        private final int failuresBeforeSuccess;
        private final AtomicInteger calls = new AtomicInteger();

        private FlakyAdapter(SourceType sourceType, int failuresBeforeSuccess) {
            this.sourceType = sourceType;
            this.failuresBeforeSuccess = failuresBeforeSuccess;
        }

        @Override
        public SourceType sourceType() {
            return sourceType;
        }

        @Override
        public SourceMetadata metadata() {
            return testMetadata();
        }

        @Override
        public FetchResult fetch(FetchContext context) {
            if (calls.incrementAndGet() <= failuresBeforeSuccess) {
                throw new IllegalStateException("feed unavailable");
            }
            return EMPTY;
        }
    }

    /** fetch 阻塞直到 release,供 bulkhead 並行測試。 */
    private static final class BlockingAdapter implements ThreatSourceAdapter {
        private final SourceType sourceType;
        private final CountDownLatch entered = new CountDownLatch(2);
        private final CountDownLatch release = new CountDownLatch(1);

        private BlockingAdapter(SourceType sourceType) {
            this.sourceType = sourceType;
        }

        @Override
        public SourceType sourceType() {
            return sourceType;
        }

        @Override
        public SourceMetadata metadata() {
            return testMetadata();
        }

        @Override
        public FetchResult fetch(FetchContext context) {
            entered.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("release 未在時限內發生");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            return EMPTY;
        }
    }

    private static SourceMetadata testMetadata() {
        return new SourceMetadata(
                "test",
                "test",
                "https://test.example.invalid",
                Set.of(IocType.URL),
                Tlp.CLEAR,
                RedistributionPolicy.INTERNAL_ONLY,
                Duration.ofSeconds(3600),
                false);
    }
}
