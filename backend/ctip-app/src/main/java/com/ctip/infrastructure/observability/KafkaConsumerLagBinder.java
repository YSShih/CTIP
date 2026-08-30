package com.ctip.infrastructure.observability;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.search.Search;

/**
 * {@code kafka.consumer.lag}(docs/spec/13-platform-ops.md §13.6)。
 *
 * <p>Micrometer 綁 Kafka client 時產生的名稱是
 * {@code kafka.consumer.fetch.manager.records.lag}(每個 topic-partition 一條),
 * 規格要的是單一 {@code kafka.consumer.lag}。這裡註冊的是那組序列的<strong>最大值</strong>——
 * consumer lag 的運維語意本來就是「最落後的分割落後多少」。
 * 一條都還沒出現時回 NaN,不回 0(0 的意思是「完全跟上」)。
 */
public class KafkaConsumerLagBinder implements MeterBinder {

    static final String NATIVE_LAG_METER = "kafka.consumer.fetch.manager.records.lag";

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("kafka.consumer.lag", registry, KafkaConsumerLagBinder::maxLag)
                .description("Kafka consumer 落後的訊息數(所有 topic-partition 的最大值)")
                .strongReference(true)
                .register(registry);
    }

    static double maxLag(MeterRegistry registry) {
        return Search.in(registry).name(NATIVE_LAG_METER).gauges().stream()
                .mapToDouble(Gauge::value)
                .filter(value -> !Double.isNaN(value))
                .max()
                .orElse(Double.NaN);
    }
}
