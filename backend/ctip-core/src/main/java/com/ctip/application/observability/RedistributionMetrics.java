package com.ctip.application.observability;

import com.ctip.sdk.RedistributionPolicy;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * {@code ctip.redistribution.filtered{policy}}(docs/spec/13-platform-ops.md §13.6):
 * 被再散布政策濾掉的來源明細筆數。
 *
 * <p>只計 {@code RedistributionFilter} 這條輸出路徑;查詢層在 SQL 內就排除的
 * INTERNAL_ONLY indicator 不會經過這裡(§7.9 的兩處實作,{@code IndicatorFilterSpecs} 是另一處)。
 */
@Component
public class RedistributionMetrics {

    private final Map<RedistributionPolicy, Counter> counters = new EnumMap<>(RedistributionPolicy.class);

    public RedistributionMetrics(MeterRegistry registry) {
        for (RedistributionPolicy policy : RedistributionPolicy.values()) {
            counters.put(
                    policy,
                    Counter.builder(CtipMetricNames.REDISTRIBUTION_FILTERED)
                            .description("被再散布政策濾掉的來源明細筆數")
                            .tag("policy", policy.name())
                            .register(registry));
        }
    }

    public void filtered(RedistributionPolicy policy) {
        counters.get(policy).increment();
    }
}
