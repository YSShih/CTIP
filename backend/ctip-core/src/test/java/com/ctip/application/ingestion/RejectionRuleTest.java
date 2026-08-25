package com.ctip.application.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctip.application.port.EventPublisherPort;
import com.ctip.domain.event.DomainEvent;
import com.ctip.domain.fingerprint.Sha256FingerprintStrategy;
import com.ctip.domain.indicator.RuleBasedThreatScorer;
import com.ctip.domain.indicator.normalization.IocNormalizers;
import com.ctip.domain.source.Reputation;
import com.ctip.domain.source.SourceId;
import com.ctip.domain.tenant.TenantId;
import com.ctip.sdk.IocHashType;
import com.ctip.sdk.IocType;
import com.ctip.sdk.RawThreatRecord;
import com.ctip.sdk.RedistributionPolicy;
import com.ctip.sdk.Severity;
import com.ctip.sdk.Tlp;
import com.ctip.testing.FixedClockPort;
import com.ctip.testing.InMemoryIndicatorRepository;
import com.ctip.testing.InMemorySourceRepository;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * 拒絕規則(docs/spec/07-domain-intel.md §7.3):八種 reason 各至少一案例;
 * 不得靜默接受、不得靜默丟棄(全部進 ingestion_rejections)。
 * ALLOWLISTED_DOMAIN 只做 exact match 且不套用於 URL——後綴比對會把
 * docs.google.com/malicious-doc 這類真實釣魚 URL 當良性丟棄。
 */
@Tag("unit")
class RejectionRuleTest {

    private static final UUID SYNC_ID = UUID.fromString("00000000-0000-0000-0000-00000000c1d0");

    private final InMemoryIndicatorRepository indicators = new InMemoryIndicatorRepository();
    private final List<RejectedRecord> rejections = new ArrayList<>();
    private final IngestionPipeline pipeline = pipeline(Set.of("allowlisted.example.com"));
    private final IngestionBatchProcessor processor =
            new IngestionBatchProcessor(pipeline, rejections::add, new IngestionSettings(true, 500));

    @Test
    void malformedValueIsRejected() {
        assertThat(runOne(record("999.1.2.3", IocType.IPV4))).isEqualTo(RejectionReason.MALFORMED_VALUE);
    }

    @Test
    void privateOrReservedIpIsRejected() {
        assertThat(runOne(record("192.168.1.50", IocType.IPV4))).isEqualTo(RejectionReason.PRIVATE_OR_RESERVED_IP);
        assertThat(runOne(record("fe80::1", IocType.IPV6))).isEqualTo(RejectionReason.PRIVATE_OR_RESERVED_IP);
    }

    @Test
    void allowlistedDomainIsExactMatchOnlyAndNotAppliedToUrls() {
        assertThat(runOne(record("Allowlisted.Example.COM", IocType.DOMAIN)))
                .isEqualTo(RejectionReason.ALLOWLISTED_DOMAIN);
        // 子網域不得後綴比對放行/拒絕:必須被接受
        assertThat(runOne(record("sub.allowlisted.example.com", IocType.DOMAIN)))
                .isNull();
        // URL 型別不套用 allowlist:必須被接受
        assertThat(runOne(record("https://allowlisted.example.com/malicious-doc", IocType.URL)))
                .isNull();
    }

    @Test
    void lengthExceededIsRejected() {
        assertThat(runOne(record("https://long.example.com/" + "a".repeat(2100), IocType.URL)))
                .isEqualTo(RejectionReason.LENGTH_EXCEEDED);
        assertThat(runOne(record("x".repeat(310) + "@long.example.com", IocType.EMAIL)))
                .isEqualTo(RejectionReason.LENGTH_EXCEEDED);
    }

    @Test
    void hashLengthMismatchIsRejected() {
        RawThreatRecord sha1LengthDeclaredSha256 = new RawThreatRecord(
                "da39a3ee5e6b4b0d3255bfef95601890afd80709",
                IocType.FILE_HASH,
                IocHashType.SHA256,
                FixedClockPort.DEFAULT_NOW,
                50,
                Severity.LOW,
                null,
                Set.of(),
                Map.of());
        assertThat(runOne(sha1LengthDeclaredSha256)).isEqualTo(RejectionReason.HASH_LENGTH_MISMATCH);
    }

    @Test
    void unknownTypeIsRejected() {
        assertThat(runOne(record("%%%not-an-ioc%%%", null))).isEqualTo(RejectionReason.UNKNOWN_TYPE);
    }

    @Test
    void duplicateInBatchRejectsSecondOccurrenceOnly() {
        BatchOutcome outcome = processor.process(
                sourceContext(),
                SYNC_ID,
                List.of(record("dup.example.com", IocType.DOMAIN), record("DUP.example.com.", IocType.DOMAIN)));
        assertThat(outcome.accepted()).isEqualTo(1);
        assertThat(outcome.rejected()).isEqualTo(1);
        assertThat(rejections.getLast().reason()).isEqualTo(RejectionReason.DUPLICATE_IN_BATCH);
    }

    @Test
    void quotaExceededIsRejected() {
        BatchState exhausted = new BatchState(SYNC_ID, 0);
        IngestionContext context =
                new IngestionContext(record("quota.example.com", IocType.DOMAIN), sourceContext(), exhausted);
        pipeline.run(context);
        assertThat(context.rejectionReason()).isEqualTo(RejectionReason.QUOTA_EXCEEDED);
    }

    @Test
    void unexpectedStageErrorIsRecordedNotSilentlyDropped() {
        IngestionStage bomb = new IngestionStage() {
            @Override
            public String name() {
                return "Bomb";
            }

            @Override
            public IngestionContext execute(IngestionContext context) {
                throw new IllegalStateException("stage exploded");
            }
        };
        IngestionBatchProcessor exploding = new IngestionBatchProcessor(
                new IngestionPipeline(List.of(bomb)), rejections::add, new IngestionSettings(true, 500));
        BatchOutcome outcome =
                exploding.process(sourceContext(), SYNC_ID, List.of(record("ok.example.com", IocType.DOMAIN)));
        assertThat(outcome.rejected()).isEqualTo(1);
        assertThat(rejections.getLast().reason()).isEqualTo(RejectionReason.MALFORMED_VALUE);
        assertThat(rejections.getLast().detail()).contains("stage exploded");
    }

    /** 跑單筆;接受回 null,拒絕回 reason(同時驗證拒絕都寫入了 rejection log)。 */
    private RejectionReason runOne(RawThreatRecord raw) {
        int before = rejections.size();
        BatchOutcome outcome = processor.process(sourceContext(), SYNC_ID, List.of(raw));
        if (outcome.rejected() == 0) {
            assertThat(rejections).hasSize(before);
            return null;
        }
        assertThat(rejections).hasSize(before + 1);
        assertThat(rejections.getLast().sourceSyncId()).isEqualTo(SYNC_ID);
        return rejections.getLast().reason();
    }

    private static RawThreatRecord record(String rawValue, IocType type) {
        return new RawThreatRecord(
                rawValue, type, null, FixedClockPort.DEFAULT_NOW, 50, Severity.MEDIUM, null, Set.of(), Map.of());
    }

    private static SourceContext sourceContext() {
        return new SourceContext(
                new SourceId(UUID.fromString("00000000-0000-0000-0000-00000000000f")),
                TenantId.PUBLIC,
                Tlp.CLEAR,
                RedistributionPolicy.ATTRIBUTION_REQUIRED,
                new Reputation(70),
                false);
    }

    private IngestionPipeline pipeline(Set<String> allowlist) {
        IocNormalizers normalizers = new IocNormalizers(false);
        Sha256FingerprintStrategy fingerprint = new Sha256FingerprintStrategy();
        AtomicLong sequence = new AtomicLong(1);
        EventPublisherPort noopEvents = (DomainEvent event) -> {};
        InstantSource clock = InstantSource.fixed(FixedClockPort.DEFAULT_NOW);
        return new IngestionPipeline(List.of(
                new ParseStage(normalizers),
                new ValidateStage(),
                new NormalizeStage(normalizers, allowlist),
                new FingerprintStage(fingerprint),
                new DeduplicateStage(indicators),
                new MergeStage(
                        () -> new UUID(0, sequence.getAndIncrement()), fingerprint, new InMemorySourceRepository()),
                new ScoreStage(new RuleBasedThreatScorer(clock)),
                new PersistStage(indicators),
                new EventPublishStage(noopEvents)));
    }
}
