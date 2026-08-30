package com.ctip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.ctip.application.ingestion.IngestionPipeline;
import com.ctip.application.observability.CtipMetricNames;
import com.ctip.infrastructure.observability.ElasticsearchClusterHealthBinder;
import com.ctip.infrastructure.observability.KafkaConsumerLagBinder;
import io.lettuce.core.metrics.MicrometerCommandLatencyRecorder;
import io.lettuce.core.metrics.MicrometerOptions;
import io.lettuce.core.protocol.CommandType;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.net.InetSocketAddress;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * DoD M3-12:Prometheus 指標齊全,含<strong>每個 ingestion stage</strong> 的耗時
 * (docs/spec/13-platform-ops.md §13.6 的必要指標清單)。
 *
 * <p>清單分兩類。無條件的(HTTP / JVM / 連線池 / 六個 {@code ctip.*})在本 context 中必須實際存在——
 * 它們全部在啟動時就註冊,不是第一次命中才出現。與後端繫結的三個({@code lettuce.*}、
 * {@code kafka.consumer.lag}、{@code elasticsearch.cluster.health})在 mvp profile 下沒有對應的
 * 後端可連,因此改為驗證「產生它們的機制確實產生這個名字」——那才是這條判準要防的迴歸。
 */
@AutoConfigureMockMvc
class MetricsCompletenessTest extends AbstractPostgresIntegrationTest {

    private static final String CLIENT_IP = "10.100.0.11";

    @Autowired
    private MeterRegistry registry;

    @Autowired
    private PrometheusMeterRegistry prometheus;

    @Autowired
    private IngestionPipeline pipeline;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private org.springframework.core.env.Environment environment;

    @BeforeEach
    void driveOneRequest() throws Exception {
        // http.server.requests 只有在有請求之後才有序列(它是 Boot 自動產生的那一組的代表)
        mvc.perform(get("/api/v1/system/health").with(request -> {
            request.setRemoteAddr(CLIENT_IP);
            return request;
        }));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                CtipMetricNames.INGESTION_RECORDS,
                CtipMetricNames.INGESTION_STAGE_DURATION,
                CtipMetricNames.SOURCE_SYNC_LAG,
                CtipMetricNames.BLOOM_GENERATION_DURATION,
                CtipMetricNames.RATELIMIT_REJECTED,
                CtipMetricNames.REDISTRIBUTION_FILTERED
            })
    void everyCtipMetricInTheSpecIsRegistered(String name) {
        assertThat(registry.find(name).meters()).as(name).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"http.server.requests", "jvm.memory.used", "jvm.gc.memory.allocated"})
    void theInfrastructureMetricsProvidedByBootAreRegistered(String name) {
        assertThat(registry.find(name).meters()).as(name).isNotEmpty();
    }

    @Test
    void theConnectionPoolMetricsAreRegistered() {
        assertThat(registry.getMeters().stream()
                        .map(meter -> meter.getId().getName())
                        .filter(name -> name.startsWith("hikaricp.connections")))
                .isNotEmpty();
    }

    /** §13.6 的強制項:每個 pipeline stage 各有一條序列(§8.2 顯式 stage 列表的直接收益)。 */
    @Test
    void everyIngestionStageHasItsOwnTimer() {
        List<String> tagged = registry.find(CtipMetricNames.INGESTION_STAGE_DURATION).timers().stream()
                .map(timer -> timer.getId().getTag("stage"))
                .toList();

        assertThat(tagged).containsExactlyInAnyOrderElementsOf(pipeline.stageNames());
    }

    /** Phase 22 判準的 curl 抓的是 Prometheus 命名法,不是 Micrometer 的名字。 */
    @Test
    void theScrapeOutputCarriesThePrometheusNames() {
        String scrape = prometheus.scrape();

        assertThat(scrape).contains("ctip_ingestion_stage_duration");
        assertThat(scrape).contains("ctip_ingestion_records_total");
        assertThat(scrape).contains("ctip_ratelimit_rejected_total");
    }

    /**
     * Prometheus 的 exemplar 必須維持關閉(ADR 0032 §15)。
     *
     * <p>它會在<strong>記錄指標的那條執行緒上</strong>向 bean factory 要 Tracer,而 Lettuce 的
     * 命令延遲是在 netty event loop 上記錄的——啟動時主執行緒握著 singleton 建立鎖等 Redis 連線,
     * 那條連線又只能由同一個 event loop 完成,兩邊互等。{@code RATE_LIMIT_BACKEND=redis} 的環境
     * (dev / staging / prod)會直接卡在啟動,而症狀是「沒有任何錯誤訊息的啟動逾時」。
     */
    @Test
    void prometheusExemplarsStayDisabled() {
        assertThat(environment.getProperty("management.tracing.exemplars.include"))
                .isEqualTo("none");
    }

    /** 三個與後端繫結的指標:驗證產生它們的機制,而不是在沒有後端的 profile 假裝它們存在。 */
    @Nested
    class BackendBoundMetrics {

        private final SimpleMeterRegistry simple = new SimpleMeterRegistry();

        @Test
        void elasticsearchClusterHealthIsBoundAsAGauge() {
            new ElasticsearchClusterHealthBinder(() -> "green").bindTo(simple);

            assertThat(simple.get("elasticsearch.cluster.health").gauge().value())
                    .isEqualTo(2);
        }

        @Test
        void kafkaConsumerLagAggregatesTheNativeMeters() {
            Gauge.builder("kafka.consumer.fetch.manager.records.lag", () -> 7.0)
                    .tag("partition", "0")
                    .register(simple);
            new KafkaConsumerLagBinder().bindTo(simple);

            assertThat(simple.get("kafka.consumer.lag").gauge().value()).isEqualTo(7);
        }

        @Test
        void lettuceCommandLatencyIsRecordedUnderTheLettucePrefix() {
            MicrometerCommandLatencyRecorder recorder =
                    new MicrometerCommandLatencyRecorder(simple, MicrometerOptions.create());

            recorder.recordCommandLatency(
                    new InetSocketAddress("127.0.0.1", 0),
                    new InetSocketAddress("127.0.0.1", 6379),
                    CommandType.GET,
                    1_000L,
                    2_000L);

            assertThat(simple.getMeters().stream()
                            .map(meter -> meter.getId().getName())
                            .filter(name -> name.startsWith("lettuce.")))
                    .isNotEmpty();
        }
    }
}
