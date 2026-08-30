package com.ctip.testing;

import com.ctip.application.ingestion.DeduplicateStage;
import com.ctip.application.ingestion.EventPublishStage;
import com.ctip.application.ingestion.FingerprintStage;
import com.ctip.application.ingestion.IngestionBatchExecutor;
import com.ctip.application.ingestion.IngestionBatchProcessor;
import com.ctip.application.ingestion.IngestionPipeline;
import com.ctip.application.ingestion.IngestionSettings;
import com.ctip.application.ingestion.MergeStage;
import com.ctip.application.ingestion.NormalizeStage;
import com.ctip.application.ingestion.ParseStage;
import com.ctip.application.ingestion.PersistStage;
import com.ctip.application.ingestion.RejectedRecord;
import com.ctip.application.ingestion.ScoreStage;
import com.ctip.application.ingestion.StixProjectionStage;
import com.ctip.application.ingestion.ValidateStage;
import com.ctip.application.port.IdGeneratorPort;
import com.ctip.application.port.StixObjectPort;
import com.ctip.application.search.SearchIndexWriter;
import com.ctip.application.stix.StixProjectionFactory;
import com.ctip.application.stix.StixProjectionWriter;
import com.ctip.domain.event.DomainEvent;
import com.ctip.domain.fingerprint.Sha256FingerprintStrategy;
import com.ctip.domain.indicator.RuleBasedThreatScorer;
import com.ctip.domain.indicator.normalization.IocNormalizers;
import com.ctip.domain.source.Reputation;
import com.ctip.domain.source.Source;
import com.ctip.domain.source.SourceHealth;
import com.ctip.domain.source.SourceId;
import com.ctip.domain.source.SourceSnapshot;
import com.ctip.domain.stix.StixProjection;
import com.ctip.sdk.RedistributionPolicy;
import com.ctip.sdk.SourceType;
import com.ctip.sdk.Tlp;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 手動提交／匯入的 pipeline 裝配(與正式裝配相同的 stage 清單,§8.2)。
 *
 * <p>手動提交的重點正是「複用同一條 pipeline」(§8.3),用假的 executor 測就等於沒測到那件事,
 * 因此這裡組的是真的 pipeline,只有持久化與事件發佈換成 in-memory。
 */
public final class ManualIngestionHarness {

    /** V4 種子的 MANUAL 來源(來源 id 固定,測試因此可斷言歸屬)。 */
    public static final SourceId MANUAL_SOURCE_ID =
            new SourceId(UUID.fromString("00000000-0000-0000-0000-0000000000aa"));

    private final InMemoryIndicatorRepository indicators = new InMemoryIndicatorRepository();
    private final InMemorySourceRepository sources = new InMemorySourceRepository();
    private final List<RejectedRecord> rejections = new ArrayList<>();
    private final List<StixProjection> projections = new ArrayList<>();
    private final List<DomainEvent> events = new ArrayList<>();
    private final IngestionBatchExecutor executor;

    public ManualIngestionHarness() {
        sources.enabledSyncable(List.of(manualSource()));
        StixObjectPort stixObjects = new RecordingStixObjects(projections);
        AtomicLong sequence = new AtomicLong(1);
        IdGeneratorPort ids = () -> new UUID(0, sequence.getAndIncrement());
        IocNormalizers normalizers = new IocNormalizers(false);
        Sha256FingerprintStrategy fingerprint = new Sha256FingerprintStrategy();
        InstantSource clock = InstantSource.fixed(FixedClockPort.DEFAULT_NOW);
        IngestionPipeline pipeline = new IngestionPipeline(List.of(
                new ParseStage(normalizers),
                new ValidateStage(),
                new NormalizeStage(normalizers, Set.of()),
                new FingerprintStage(fingerprint),
                new DeduplicateStage(indicators),
                new MergeStage(ids, fingerprint, sources),
                new ScoreStage(new RuleBasedThreatScorer(clock)),
                new StixProjectionStage(
                        new StixProjectionFactory(sources, stixObjects, FixedClockPort.at(FixedClockPort.DEFAULT_NOW))),
                new PersistStage(indicators),
                new EventPublishStage(events::add)));
        this.executor = new IngestionBatchExecutor(
                new IngestionBatchProcessor(pipeline, rejections::add, new IngestionSettings(true, 500)),
                new StixProjectionWriter(stixObjects),
                new SearchIndexWriter(new InMemorySearchDocuments(), new InMemorySearchIndex()));
    }

    public IngestionBatchExecutor executor() {
        return executor;
    }

    public InMemorySourceRepository sources() {
        return sources;
    }

    public InMemoryIndicatorRepository indicators() {
        return indicators;
    }

    public List<RejectedRecord> rejections() {
        return rejections;
    }

    public List<StixProjection> projections() {
        return projections;
    }

    /** 只記錄 upsert;其餘讀取在手動提交路徑上不會被呼叫。 */
    private record RecordingStixObjects(List<StixProjection> written) implements StixObjectPort {

        @Override
        public java.util.Optional<java.time.Instant> findCreated(String stixId) {
            return java.util.Optional.empty();
        }

        @Override
        public void upsert(StixProjection projection) {
            written.add(projection);
        }

        @Override
        public java.util.Optional<String> findContent(String stixId) {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.Optional<com.ctip.domain.stix.StixOrigin> findOrigin(String stixId) {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.Map<String, String> findContents(java.util.Collection<String> stixIds) {
            return java.util.Map.of();
        }
    }

    private static Source manualSource() {
        return Source.reconstitute(new SourceSnapshot(
                MANUAL_SOURCE_ID,
                SourceType.MANUAL,
                "Manual Submission",
                null,
                Tlp.AMBER,
                RedistributionPolicy.INTERNAL_ONLY,
                new Reputation(50),
                true,
                false,
                null,
                SourceHealth.initial(),
                null,
                null,
                0));
    }
}
