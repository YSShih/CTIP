package com.ctip.domain.indicator;

import static com.ctip.testing.IndicatorTestBuilder.DEMO_TENANT;
import static com.ctip.testing.IndicatorTestBuilder.SOURCE_A;
import static com.ctip.testing.IndicatorTestBuilder.SOURCE_B;
import static com.ctip.testing.IndicatorTestBuilder.SOURCE_C;
import static com.ctip.testing.IndicatorTestBuilder.T0;
import static com.ctip.testing.IndicatorTestBuilder.domainValue;
import static com.ctip.testing.IndicatorTestBuilder.report;
import static org.assertj.core.api.Assertions.assertThat;

import com.ctip.domain.fingerprint.Sha256FingerprintStrategy;
import com.ctip.domain.source.Reputation;
import com.ctip.domain.source.SourceId;
import com.ctip.sdk.Confidence;
import com.ctip.sdk.Severity;
import java.time.Duration;
import java.time.InstantSource;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * RuleBasedThreatScorer 四項權重(docs/spec/07-domain-intel.md §7.6):
 * confidence 40% + severity 25% + 獨立 ACTIVE 來源數 20% + recency 15%。
 */
@Tag("unit")
class ThreatScorerTest {

    private static final SourceId SOURCE_D = new SourceId(UUID.fromString("00000000-0000-0000-0000-0000000000d4"));
    private static final SourceId SOURCE_E = new SourceId(UUID.fromString("00000000-0000-0000-0000-0000000000e5"));

    @Test
    void allComponentsAtMaximumYieldOneHundred() {
        Indicator indicator = newIndicator(SOURCE_A, Confidence.of(100), Severity.CRITICAL);
        merge(indicator, SOURCE_B, Confidence.of(100), Severity.CRITICAL);
        merge(indicator, SOURCE_C, Confidence.of(100), Severity.CRITICAL);
        merge(indicator, SOURCE_D, Confidence.of(100), Severity.CRITICAL);
        merge(indicator, SOURCE_E, Confidence.of(100), Severity.CRITICAL);

        indicator.applyScore(new RuleBasedThreatScorer(InstantSource.fixed(T0)));
        // 40(confidence 100)+ 25(CRITICAL)+ 20(5 來源達上限)+ 15(recency 0 天)
        assertThat(indicator.score()).isEqualTo(100);
    }

    @Test
    void confidenceAndScoreUseTheSameActiveSourceCountDefinition() {
        Indicator indicator = newIndicator(SOURCE_A, Confidence.of(80), Severity.MEDIUM);
        merge(indicator, SOURCE_B, Confidence.of(60), Severity.MEDIUM);
        indicator.mergeFrom(
                new IndicatorSource(report(SOURCE_C)
                        .confidence(Confidence.of(90))
                        .status(SourceRecordStatus.RETRACTED)
                        .build()),
                new Reputation(50));

        // 三筆來源記錄、兩筆 ACTIVE:兩處的「來源數」皆為獨立 ACTIVE 來源數(= 2)。
        assertThat(IndicatorMergePolicy.activeSourceCount(indicator.snapshot().sources().stream()
                        .map(IndicatorSource::new)
                        .toList()))
                .isEqualTo(2);
        // confidence:RETRACTED 不計入加權,2 < 3 無 +10 加成 → (80×50 + 60×50) / 100 = 70
        assertThat(indicator.confidence().value()).isEqualTo(70);

        indicator.applyScore(new RuleBasedThreatScorer(InstantSource.fixed(T0)));
        // 28(70×0.4)+ 12.5(MEDIUM)+ 12.26(log(1+2)/log(6)×20,n=2 非 3)+ 15 = 67.76 → 68
        // 若評分改以全部來源數 n=3 計,結果會是 71——此斷言鎖定兩處定義一致
        assertThat(indicator.score()).isEqualTo(68);
    }

    @Test
    void recencyDecaysWithThirtyDayHalfLife() {
        Indicator indicator = newIndicator(SOURCE_A, null, Severity.MEDIUM);

        indicator.applyScore(new RuleBasedThreatScorer(InstantSource.fixed(T0.plus(Duration.ofDays(30)))));
        // 20(中性 confidence 50)+ 12.5(MEDIUM)+ 7.74(單一來源)+ 7.5(0.5¹×15)= 47.74 → 48
        assertThat(indicator.score()).isEqualTo(48);
    }

    @Test
    void sourceCountComponentIsCappedAtFiveSources() {
        Indicator five = newIndicator(SOURCE_A, Confidence.of(0), Severity.INFO);
        merge(five, SOURCE_B, Confidence.of(0), Severity.INFO);
        merge(five, SOURCE_C, Confidence.of(0), Severity.INFO);
        merge(five, SOURCE_D, Confidence.of(0), Severity.INFO);
        merge(five, SOURCE_E, Confidence.of(0), Severity.INFO);

        Indicator six = newIndicator(SOURCE_A, Confidence.of(0), Severity.INFO);
        merge(six, SOURCE_B, Confidence.of(0), Severity.INFO);
        merge(six, SOURCE_C, Confidence.of(0), Severity.INFO);
        merge(six, SOURCE_D, Confidence.of(0), Severity.INFO);
        merge(six, SOURCE_E, Confidence.of(0), Severity.INFO);
        merge(
                six,
                new SourceId(UUID.fromString("00000000-0000-0000-0000-0000000000f6")),
                Confidence.of(0),
                Severity.INFO);

        RuleBasedThreatScorer scorer = new RuleBasedThreatScorer(InstantSource.fixed(T0));
        five.applyScore(scorer);
        six.applyScore(scorer);
        // 4(confidence 0+10 加成 → 10)+ 0(INFO)+ 20(上限)+ 15 = 39;第六個來源不再增加
        assertThat(five.score()).isEqualTo(39);
        assertThat(six.score()).isEqualTo(five.score());
    }

    private static Indicator newIndicator(SourceId first, Confidence confidence, Severity severity) {
        NewIndicatorCommand cmd = new NewIndicatorCommand(
                new IndicatorId(UUID.fromString("00000000-0000-0000-0000-00000000cafe")),
                DEMO_TENANT,
                domainValue("mal-example.ctip-sample.net"),
                report(first).confidence(confidence).severity(severity).build(),
                new Reputation(50));
        return Indicator.create(cmd, new Sha256FingerprintStrategy());
    }

    private static void merge(Indicator indicator, SourceId sourceId, Confidence confidence, Severity severity) {
        indicator.mergeFrom(
                new IndicatorSource(report(sourceId)
                        .confidence(confidence)
                        .severity(severity)
                        .build()),
                new Reputation(50));
    }
}
