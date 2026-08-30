package com.ctip.infrastructure.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctip.application.observability.CtipMetricNames;
import com.ctip.application.port.RateLimitKey;
import com.ctip.domain.plan.EndpointClass;
import com.ctip.domain.tenant.TenantId;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** {@code ctip.ratelimit.rejected{dimension}}(docs/spec/13-platform-ops.md §13.6)。 */
@Tag("unit")
class RateLimitMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final RateLimitMetrics metrics = new RateLimitMetrics(registry);

    /** 剛啟動的實例也要看得到 0,而不是 no data。 */
    @Test
    void everyDimensionIsRegisteredUpFront() {
        assertThat(registry.find(CtipMetricNames.RATELIMIT_REJECTED).counters()).hasSize(6);
    }

    @Test
    void theTagIsTheDimensionNotTheSubject() {
        RateLimitKey key = RateLimitKey.tenant(TenantId.PUBLIC, RateLimitKey.Window.MINUTE);

        metrics.rejected(key);

        assertThat(registry.get(CtipMetricNames.RATELIMIT_REJECTED)
                        .tag("dimension", "tenant")
                        .counter()
                        .count())
                .isEqualTo(1);
    }

    /** 維度 5 不分 scope:它問的是「哪一類端點被擋」。 */
    @Test
    void theEndpointClassDimensionIsItsOwnBucket() {
        RateLimitKey key =
                RateLimitKey.tenant(TenantId.PUBLIC, RateLimitKey.Window.MINUTE).inClass(EndpointClass.HEAVY);

        metrics.rejected(key);

        assertThat(registry.get(CtipMetricNames.RATELIMIT_REJECTED)
                        .tag("dimension", RateLimitMetrics.ENDPOINT_CLASS)
                        .counter()
                        .count())
                .isEqualTo(1);
        assertThat(registry.get(CtipMetricNames.RATELIMIT_REJECTED)
                        .tag("dimension", "tenant")
                        .counter()
                        .count())
                .isZero();
    }
}
