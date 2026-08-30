package com.ctip.infrastructure.observability;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.util.Locale;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code elasticsearch.cluster.health}(docs/spec/13-platform-ops.md §13.6)。
 * Boot 4 的 {@code spring-boot-elasticsearch} 沒有任何 metrics autoconfig,這條要自己綁。
 *
 * <p>值域對齊 ES 自己的顏色:green=2、yellow=1、red=0、查不到=NaN。
 * 查詢失敗一律回 NaN 並只記一則 debug——§13.7 明令 ES 不可用不得影響應用,
 * 而抓取指標的頻率是每 15 秒,連不上時每次都記 WARN 只會淹掉日誌。
 */
public class ElasticsearchClusterHealthBinder implements MeterBinder {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchClusterHealthBinder.class);

    private final Supplier<String> statusSupplier;

    public ElasticsearchClusterHealthBinder(Supplier<String> statusSupplier) {
        this.statusSupplier = statusSupplier;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("elasticsearch.cluster.health", this, ElasticsearchClusterHealthBinder::currentValue)
                .description("Elasticsearch 叢集健康(green=2、yellow=1、red=0、不可用=NaN)")
                .strongReference(true)
                .register(registry);
    }

    private double currentValue() {
        try {
            return value(statusSupplier.get());
        } catch (RuntimeException e) {
            log.debug("Elasticsearch 叢集健康查詢失敗", e);
            return Double.NaN;
        }
    }

    static double value(String status) {
        if (status == null) {
            return Double.NaN;
        }
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "green" -> 2;
            case "yellow" -> 1;
            case "red" -> 0;
            default -> Double.NaN;
        };
    }
}
