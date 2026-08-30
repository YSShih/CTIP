package com.ctip.infrastructure.observability;

import com.ctip.application.observability.CtipMetricNames;
import com.ctip.application.port.RateLimitKey;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * {@code ctip.ratelimit.rejected{dimension}}(docs/spec/13-platform-ops.md §13.6)。
 *
 * <p>tag 是 §10.7 的<strong>維度</strong>而不是 {@link RateLimitKey#asString()}——
 * subject 是 API key id / user id / tenant id / IP,放進 tag 等於讓序列數隨呼叫者數量成長。
 * 六個值在建構時全部註冊,剛啟動的實例也看得到 0。
 */
@Component
public class RateLimitMetrics {

    /** 維度 5 的計數不分 scope:它問的是「哪一類端點被擋」,而不是「誰被擋」。 */
    static final String ENDPOINT_CLASS = "endpoint-class";

    private static final List<String> DIMENSIONS = List.of("key", "user", "tenant", "ip", "submit", ENDPOINT_CLASS);

    private final Map<String, Counter> counters = new LinkedHashMap<>();

    public RateLimitMetrics(MeterRegistry registry) {
        for (String dimension : DIMENSIONS) {
            counters.put(
                    dimension,
                    Counter.builder(CtipMetricNames.RATELIMIT_REJECTED)
                            .description("被限流拒絕的請求數")
                            .tag("dimension", dimension)
                            .register(registry));
        }
    }

    public void rejected(RateLimitKey key) {
        Counter counter = counters.get(dimensionOf(key));
        if (counter != null) {
            counter.increment();
        }
    }

    static String dimensionOf(RateLimitKey key) {
        return key.endpointClass() == null ? key.scope() : ENDPOINT_CLASS;
    }
}
