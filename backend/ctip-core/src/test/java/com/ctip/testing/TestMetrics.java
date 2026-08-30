package com.ctip.testing;

import com.ctip.application.observability.BloomMetrics;
import com.ctip.application.observability.IngestionMetrics;
import com.ctip.application.observability.RedistributionMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * 單元測試用的指標門面(13 §13.6 的產生端在 application 層,因此每個手動 new 的
 * 服務都需要一份)。每次 {@code new} 都是獨立的 {@link SimpleMeterRegistry},
 * 測試之間不共享計數。
 */
public final class TestMetrics {

    private final MeterRegistry registry = new SimpleMeterRegistry();

    public MeterRegistry registry() {
        return registry;
    }

    public IngestionMetrics ingestion() {
        return new IngestionMetrics(registry);
    }

    public BloomMetrics bloom() {
        return new BloomMetrics(registry);
    }

    public RedistributionMetrics redistribution() {
        return new RedistributionMetrics(registry);
    }

    /** 便利建構:多數測試只需要一個能用的實例。 */
    public static IngestionMetrics ingestionMetrics() {
        return new TestMetrics().ingestion();
    }

    public static BloomMetrics bloomMetrics() {
        return new TestMetrics().bloom();
    }

    public static RedistributionMetrics redistributionMetrics() {
        return new TestMetrics().redistribution();
    }
}
