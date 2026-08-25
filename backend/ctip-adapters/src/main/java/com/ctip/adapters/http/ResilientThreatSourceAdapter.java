package com.ctip.adapters.http;

import com.ctip.sdk.FetchContext;
import com.ctip.sdk.FetchResult;
import com.ctip.sdk.SourceMetadata;
import com.ctip.sdk.SourceType;
import com.ctip.sdk.ThreatSourceAdapter;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import java.util.function.Supplier;

/**
 * {@link FetchResilience#decorate} 的回傳型別:fetch 依
 * Retry(CircuitBreaker(Bulkhead(call))) 疊加;sourceType 與 metadata 原樣委派。
 */
final class ResilientThreatSourceAdapter implements ThreatSourceAdapter {

    private final ThreatSourceAdapter delegate;
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;
    private final Bulkhead bulkhead;

    ResilientThreatSourceAdapter(
            ThreatSourceAdapter delegate, Retry retry, CircuitBreaker circuitBreaker, Bulkhead bulkhead) {
        this.delegate = delegate;
        this.retry = retry;
        this.circuitBreaker = circuitBreaker;
        this.bulkhead = bulkhead;
    }

    @Override
    public SourceType sourceType() {
        return delegate.sourceType();
    }

    @Override
    public SourceMetadata metadata() {
        return delegate.metadata();
    }

    @Override
    public FetchResult fetch(FetchContext context) {
        Supplier<FetchResult> call = Bulkhead.decorateSupplier(bulkhead, () -> delegate.fetch(context));
        call = CircuitBreaker.decorateSupplier(circuitBreaker, call);
        call = Retry.decorateSupplier(retry, call);
        return call.get();
    }
}
