package com.ctip.application.source;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctip.application.ingestion.IngestionBatchExecutor;
import com.ctip.application.ingestion.IngestionBatchProcessor;
import com.ctip.application.ingestion.IngestionPipeline;
import com.ctip.application.ingestion.IngestionSettings;
import com.ctip.application.port.AdapterRegistryPort;
import com.ctip.application.port.SourceSyncLogPort;
import com.ctip.application.stix.StixProjectionWriter;
import com.ctip.domain.event.SourceEvents.SourceDegraded;
import com.ctip.domain.event.SourceEvents.SourceFailed;
import com.ctip.domain.event.SourceEvents.SourceRecovered;
import com.ctip.domain.source.Reputation;
import com.ctip.domain.source.Source;
import com.ctip.domain.source.SourceHealth;
import com.ctip.domain.source.SourceId;
import com.ctip.domain.source.SourceSnapshot;
import com.ctip.domain.source.SourceStatus;
import com.ctip.sdk.FetchContext;
import com.ctip.sdk.FetchResult;
import com.ctip.sdk.IocType;
import com.ctip.sdk.RawThreatRecord;
import com.ctip.sdk.RedistributionPolicy;
import com.ctip.sdk.SourceMetadata;
import com.ctip.sdk.SourceType;
import com.ctip.sdk.ThreatSourceAdapter;
import com.ctip.sdk.Tlp;
import com.ctip.testing.FixedClockPort;
import com.ctip.testing.InMemorySourceRepository;
import com.ctip.testing.RecordingEventPublisher;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * 來源健康狀態機(S2–S4)在 service 邊界的行為 + SourceSyncService 的逐一處理
 * (docs/spec/08-ingestion-sdk.md §8.5–8.6):單一來源失敗不影響其他來源。
 */
@Tag("unit")
class SourceHealthTest {

    private static final Instant NOW = FixedClockPort.DEFAULT_NOW;

    private final InMemorySourceRepository sources = new InMemorySourceRepository();
    private final RecordingEventPublisher events = new RecordingEventPublisher();
    private final FixedClockPort clock = FixedClockPort.at(NOW);
    private final SourceHealthService healthService = new SourceHealthService(sources, events, clock);

    @Test
    void successResetsFailuresPersistsCursorAndPublishesRecovery() {
        Source source = syncable(SourceType.MOCK_OPENPHISH, 4); // 已 DEGRADED
        assertThat(source.health().status()).isEqualTo(SourceStatus.DEGRADED);

        healthService.recordSuccess(source, 42, Duration.ofMillis(120), "17");

        Source persisted = sources.saved().getLast();
        assertThat(persisted.health().status()).isEqualTo(SourceStatus.ACTIVE);
        assertThat(persisted.health().consecutiveFailures()).isZero();
        assertThat(persisted.totalRecordsIngested()).isEqualTo(42);
        assertThat(persisted.nextCursor()).isEqualTo("17");
        assertThat(events.published()).hasSize(1).first().isInstanceOf(SourceRecovered.class);
    }

    @Test
    void thirdFailureDegradesAndTenthFails() {
        Source source = syncable(SourceType.MOCK_OPENPHISH, 0);
        for (int i = 0; i < 3; i++) {
            healthService.recordFailure(source, "boom");
        }
        assertThat(source.health().status()).isEqualTo(SourceStatus.DEGRADED);
        assertThat(events.published())
                .filteredOn(SourceDegraded.class::isInstance)
                .hasSize(1);

        for (int i = 0; i < 7; i++) {
            healthService.recordFailure(source, "boom");
        }
        assertThat(source.health().status()).isEqualTo(SourceStatus.FAILED);
        assertThat(source.health().consecutiveFailures()).isEqualTo(10);
        assertThat(events.published())
                .filteredOn(SourceFailed.class::isInstance)
                .hasSize(1);
        assertThat(sources.saved()).hasSize(10);
    }

    @Test
    void failureMessagesAreMaskedBeforePersistence() {
        Source source = syncable(SourceType.MOCK_OPENPHISH, 0);
        healthService.recordFailure(source, "fetch failed: token=VERY_SECRET_TOKEN");
        assertThat(sources.saved().getLast().lastErrorMessage())
                .doesNotContain("VERY_SECRET_TOKEN")
                .contains("token=***");
    }

    @Test
    void syncProcessesEachDueSourceAndOneFailureDoesNotAffectOthers() {
        Source failing = syncable(SourceType.MOCK_OPENPHISH, 0);
        Source healthy = syncable(SourceType.MOCK_ABUSEIPDB, 0);
        sources.enabledSyncable(List.of(failing, healthy));

        Map<SourceType, ThreatSourceAdapter> adapters = new HashMap<>();
        adapters.put(SourceType.MOCK_OPENPHISH, new ThrowingAdapter(SourceType.MOCK_OPENPHISH));
        adapters.put(SourceType.MOCK_ABUSEIPDB, new PagedAdapter(SourceType.MOCK_ABUSEIPDB, 3, 2));
        SourceSyncService sync = syncService(adapters);

        List<SourceSyncOutcome> outcomes = sync.syncDueSources();

        assertThat(outcomes).hasSize(2);
        assertThat(outcomes.getFirst().success()).isFalse();
        assertThat(outcomes.getLast().success()).isTrue();
        assertThat(outcomes.getLast().recordsFetched()).isEqualTo(6); // 3 頁 × 2 筆,分頁全部走完
        assertThat(failing.health().consecutiveFailures()).isEqualTo(1);
        assertThat(healthy.health().status()).isEqualTo(SourceStatus.ACTIVE);
        assertThat(healthy.totalRecordsIngested()).isEqualTo(6);
        assertThat(healthy.nextCursor()).isNull(); // 抓到底,游標歸零
    }

    @Test
    void sourceWithoutAdapterIsRecordedAsFailure() {
        Source orphan = syncable(SourceType.MOCK_ALIENVAULT, 0);
        sources.enabledSyncable(List.of(orphan));
        SourceSyncService sync = syncService(Map.of());

        List<SourceSyncOutcome> outcomes = sync.syncDueSources();

        assertThat(outcomes.getFirst().success()).isFalse();
        assertThat(orphan.health().consecutiveFailures()).isEqualTo(1);
        assertThat(orphan.lastErrorMessage()).contains("MOCK_ALIENVAULT");
    }

    @Test
    void notDueSourcesAreSkipped() {
        Source recentlySynced = syncable(SourceType.MOCK_OPENPHISH, 0);
        recentlySynced.recordSuccess(1, Duration.ofMillis(5), NOW.minus(Duration.ofMinutes(10)));
        sources.enabledSyncable(List.of(recentlySynced)); // interval 1h,10 分鐘前剛同步

        SourceSyncService sync =
                syncService(Map.of(SourceType.MOCK_OPENPHISH, new PagedAdapter(SourceType.MOCK_OPENPHISH, 1, 1)));
        assertThat(sync.syncDueSources()).isEmpty();
    }

    private SourceSyncService syncService(Map<SourceType, ThreatSourceAdapter> adapters) {
        AdapterRegistryPort registry = type -> Optional.ofNullable(adapters.get(type));
        IngestionBatchProcessor processor = new IngestionBatchProcessor(
                new IngestionPipeline(List.of()), r -> {}, new IngestionSettings(true, 500));
        IngestionBatchExecutor executor =
                new IngestionBatchExecutor(processor, new StixProjectionWriter(new NoopStixObjects()));
        SourceSyncRecorder recorder = new SourceSyncRecorder(new NoopSyncLog(), healthService, events, clock);
        return new SourceSyncService(sources, registry, executor, recorder, clock);
    }

    /** 測試用 no-op stix_objects port(投影行為由 IngestionEndToEndTest 驗證)。 */
    private static final class NoopStixObjects implements com.ctip.application.port.StixObjectPort {
        @Override
        public Optional<java.time.Instant> findCreated(String stixId) {
            return Optional.empty();
        }

        @Override
        public void upsert(com.ctip.domain.stix.StixProjection projection) {
            // no-op
        }

        @Override
        public Optional<String> findContent(String stixId) {
            return Optional.empty();
        }

        @Override
        public Map<String, String> findContents(java.util.Collection<String> stixIds) {
            return Map.of();
        }
    }

    /** 測試用 no-op source_sync log(source_sync 表的行為由 IngestionEndToEndTest 驗證)。 */
    private static final class NoopSyncLog implements SourceSyncLogPort {
        @Override
        public UUID start(SourceId sourceId, java.time.Instant startedAt) {
            return UUID.nameUUIDFromBytes(
                    sourceId.value().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        @Override
        public void finish(SourceSyncReport report) {
            // no-op
        }
    }

    private static Source syncable(SourceType type, int priorFailures) {
        SourceHealth health = SourceHealth.initial();
        for (int i = 0; i < priorFailures; i++) {
            health = health.afterFailure(NOW.minus(Duration.ofHours(2)));
        }
        return Source.reconstitute(new SourceSnapshot(
                new SourceId(UUID.nameUUIDFromBytes(type.name().getBytes(java.nio.charset.StandardCharsets.UTF_8))),
                type,
                type.name(),
                Tlp.CLEAR,
                RedistributionPolicy.ATTRIBUTION_REQUIRED,
                new Reputation(70),
                true,
                true,
                Duration.ofHours(1),
                health,
                null,
                null,
                0));
    }

    /** 固定頁數 × 每頁筆數的假 adapter。 */
    private static final class PagedAdapter implements ThreatSourceAdapter {
        private final SourceType type;
        private final int pages;
        private final int perPage;

        private PagedAdapter(SourceType type, int pages, int perPage) {
            this.type = type;
            this.pages = pages;
            this.perPage = perPage;
        }

        @Override
        public SourceType sourceType() {
            return type;
        }

        @Override
        public SourceMetadata metadata() {
            return metadataFor(type);
        }

        @Override
        public FetchResult fetch(FetchContext context) {
            int page = context.cursor() == null ? 0 : Integer.parseInt(context.cursor());
            List<RawThreatRecord> records = new ArrayList<>();
            for (int i = 0; i < perPage; i++) {
                records.add(new RawThreatRecord(
                        "203.0.113." + (page * perPage + i),
                        IocType.IPV4,
                        null,
                        NOW.minus(Duration.ofDays(1)),
                        50,
                        null,
                        null,
                        Set.of(),
                        Map.of()));
            }
            boolean hasMore = page + 1 < pages;
            return new FetchResult(records, hasMore ? String.valueOf(page + 1) : null, hasMore);
        }
    }

    private static final class ThrowingAdapter implements ThreatSourceAdapter {
        private final SourceType type;

        private ThrowingAdapter(SourceType type) {
            this.type = type;
        }

        @Override
        public SourceType sourceType() {
            return type;
        }

        @Override
        public SourceMetadata metadata() {
            return metadataFor(type);
        }

        @Override
        public FetchResult fetch(FetchContext context) {
            throw new IllegalStateException("feed unavailable");
        }
    }

    private static SourceMetadata metadataFor(SourceType type) {
        return new SourceMetadata(
                type.name(),
                "test",
                "https://test.example.invalid",
                Set.of(IocType.IPV4),
                Tlp.CLEAR,
                RedistributionPolicy.ATTRIBUTION_REQUIRED,
                Duration.ofHours(1),
                false);
    }
}
