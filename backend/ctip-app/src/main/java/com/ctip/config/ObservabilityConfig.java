package com.ctip.config;

import com.ctip.application.port.ClockPort;
import com.ctip.application.port.SourceRepository;
import com.ctip.infrastructure.observability.KafkaConsumerLagBinder;
import com.ctip.infrastructure.observability.PrometheusAccessFilter;
import com.ctip.infrastructure.observability.SourceSyncLagBinder;
import com.ctip.infrastructure.observability.TraceIdFilter;
import com.ctip.infrastructure.observability.TracingAspect;
import com.ctip.infrastructure.web.FilterErrorWriter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.micrometer.metrics.autoconfigure.MeterRegistryCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * 監控裝配(docs/spec/13-platform-ops.md §13.6)。指標的<strong>產生點</strong>散在
 * application 層與各 infrastructure adapter;這裡只負責裝配與那些沒有 autoconfig 的 binder。
 *
 * <p>Boot 4 自動提供的:{@code http.server.requests}、{@code jvm.*}、{@code hikaricp.connections.*}、
 * {@code kafka.consumer.*}。需要自己綁的:來源同步落後、Kafka lag 的彙總視角、
 * Elasticsearch 叢集健康({@link SearchConfig})、Lettuce 命令延遲({@link RedisConfig})。
 */
@Configuration(proxyBeanMethods = false)
public class ObservabilityConfig {

    /**
     * 所有指標都帶上 {@code application} 與 {@code environment}:同一個 Prometheus 抓多個環境時,
     * 沒有這兩個 tag 的序列在告警規則裡分不出來(Prometheus 端的 external_labels 只在該實例內有效)。
     */
    @Bean
    MeterRegistryCustomizer<MeterRegistry> ctipCommonTags(Environment environment) {
        return registry -> registry.config()
                .meterFilter(MeterFilter.commonTags(Tags.of(
                        "application",
                        "ctip-backend",
                        "environment",
                        environment.getProperty("ctip.environment", "unknown"))));
    }

    @Bean
    SourceSyncLagBinder sourceSyncLagBinder(SourceRepository sources, ClockPort clock) {
        return new SourceSyncLagBinder(sources, clock);
    }

    /** Kafka 只屬 staging/prod(§5.5 的 {@code NOTIFICATION_TRANSPORT});in-process 時沒有 consumer 可言。 */
    @Bean
    @ConditionalOnProperty(prefix = "ctip.notification", name = "transport", havingValue = "kafka")
    KafkaConsumerLagBinder kafkaConsumerLagBinder() {
        return new KafkaConsumerLagBinder();
    }

    /**
     * {@code /actuator/prometheus} 的來源 IP 限制(§13.6)。排在 {@link TraceIdFilter} 之後,
     * 被拒絕的抓取才會拿到帶 traceId 的統一錯誤結構(§9.4)。
     */
    @Bean
    FilterRegistrationBean<PrometheusAccessFilter> prometheusAccessFilter(
            CtipProperties properties, FilterErrorWriter errorWriter) {
        FilterRegistrationBean<PrometheusAccessFilter> registration = new FilterRegistrationBean<>(
                new PrometheusAccessFilter(properties.observability().prometheusAllowedCidrs(), errorWriter));
        registration.setOrder(TraceIdFilter.ORDER + 1);
        return registration;
    }

    /** 追蹤鏈的 span 建立點:API → application service → DB / Redis / Kafka / ES(§13.6)。 */
    @Bean
    TracingAspect tracingAspect(ObservationRegistry observations) {
        return new TracingAspect(observations);
    }
}
