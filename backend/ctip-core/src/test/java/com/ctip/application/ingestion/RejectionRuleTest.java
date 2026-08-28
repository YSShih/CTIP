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
    private static final IngestionRun RUN = IngestionRun.forSourceSync(SYNC_ID);

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
    void rawValueExceedingStorageCapIsRejectedEvenWhenCleanedFits() {
        // 前置零寬字元:清理後 2040 ≤ 2048 通過型別上限,但原始值 2540 超過 DB VARCHAR(2048)
        // ——不擋下會在批次交易 flush 期炸掉整批(含拒絕記錄)
        String raw = "\u200b".repeat(500) + "https://long.example.com/" + "a".repeat(2015);
        assertThat(runOne(record(raw, IocType.URL))).isEqualTo(RejectionReason.LENGTH_EXCEEDED);
    }

    @Test
    void normalizedValueExceedingStorageCapIsRejected() {
        // 無 path 的 URL 正規化會補「/」:cleaned 恰為 2048 通過 Validate,
        // normalized 2049 > 2048 必須在 NormalizeStage 擋下,同樣是 flush 期整批炸掉的防線
        String host = ("a".repeat(61) + ".").repeat(32) + "a".repeat(56);
        String url = "https://" + host;
        assertThat(url).hasSize(2048);
        assertThat(runOne(record(url, IocType.URL))).isEqualTo(RejectionReason.LENGTH_EXCEEDED);
    }

    @Test
    void redSourceTlpIsRejected() {
        // §7.7:RED 不進入平台,ingestion 一律拒絕(reason = MALFORMED_VALUE,detail 固定)
        SourceContext redSource = new SourceContext(
                new SourceId(UUID.fromString("00000000-0000-0000-0000-0000000000ed")),
                TenantId.PUBLIC,
                Tlp.RED,
                RedistributionPolicy.INTERNAL_ONLY,
                new Reputation(70),
                false);
        BatchOutcome outcome =
                processor.process(redSource, RUN, List.of(record("red-feed.example.com", IocType.DOMAIN)));
        assertThat(outcome.accepted()).isZero();
        assertThat(outcome.rejected()).isEqualTo(1);
        assertThat(rejections.getLast().reason()).isEqualTo(RejectionReason.MALFORMED_VALUE);
        assertThat(rejections.getLast().detail()).isEqualTo("TLP:RED not accepted");
    }

    @Test
    void allowlistEntriesAreNormalizedBeforeMatching() {
        // 設定端寫「大小寫混用 + 尾點 + 空白」也必須比對得中——否則 allowlist 靜默失效
        IngestionPipeline sloppyAllowlist = pipeline(Set.of("  ALLOWLISTED.Example.COM.  "));
        IngestionBatchProcessor sloppyProcessor =
                new IngestionBatchProcessor(sloppyAllowlist, rejections::add, new IngestionSettings(true, 500));
        BatchOutcome outcome = sloppyProcessor.process(
                sourceContext(), RUN, List.of(record("allowlisted.example.com", IocType.DOMAIN)));
        assertThat(outcome.rejected()).isEqualTo(1);
        assertThat(rejections.getLast().reason()).isEqualTo(RejectionReason.ALLOWLISTED_DOMAIN);
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
                RUN,
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
                exploding.process(sourceContext(), RUN, List.of(record("ok.example.com", IocType.DOMAIN)));
        assertThat(outcome.rejected()).isEqualTo(1);
        assertThat(rejections.getLast().reason()).isEqualTo(RejectionReason.MALFORMED_VALUE);
        assertThat(rejections.getLast().detail()).contains("stage exploded");
    }

    /** 跑單筆;接受回 null,拒絕回 reason(同時驗證拒絕都寫入了 rejection log)。 */
    private RejectionReason runOne(RawThreatRecord raw) {
        int before = rejections.size();
        BatchOutcome outcome = processor.process(sourceContext(), RUN, List.of(raw));
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
