package com.ctip.application.observability;

import com.ctip.domain.bloom.BloomScope;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * {@code ctip.bloom.generation.duration{scope}}(docs/spec/13-platform-ops.md §13.6)。
 * tag 只放 scope,不放 tenantId——租戶數是無上限的,放進 tag 會讓序列數隨租戶成長。
 */
@Component
public class BloomMetrics {

    private final Map<BloomScope, Timer> timers = new EnumMap<>(BloomScope.class);

    public BloomMetrics(MeterRegistry registry) {
        for (BloomScope scope : BloomScope.values()) {
            timers.put(
                    scope,
                    Timer.builder(CtipMetricNames.BLOOM_GENERATION_DURATION)
                            .description("Bloom artifact 生成耗時")
                            .tag("scope", scope.name())
                            .register(registry));
        }
    }

    public <T> T time(BloomScope scope, Supplier<T> generation) {
        return timers.get(scope).record(generation);
    }
}
