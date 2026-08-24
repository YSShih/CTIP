package com.ctip.domain.indicator;

import static com.ctip.testing.IndicatorTestBuilder.SOURCE_A;
import static com.ctip.testing.IndicatorTestBuilder.SOURCE_B;
import static com.ctip.testing.IndicatorTestBuilder.report;
import static org.assertj.core.api.Assertions.assertThat;

import com.ctip.domain.source.Reputation;
import com.ctip.domain.source.SourceId;
import com.ctip.sdk.Confidence;
import com.ctip.sdk.IocType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** I10 加權公式與 I6/I11 的邊界分支(docs/spec/07-domain-intel.md §7.5)。 */
@Tag("unit")
class IndicatorMergePolicyTest {

    @Test
    void confidenceFallsBackToNeutralWhenNoActiveSourceProvidesIt() {
        List<IndicatorSource> records =
                List.of(new IndicatorSource(report(SOURCE_A).confidence(null).build()));
        assertThat(IndicatorMergePolicy.aggregateConfidence(records, Map.of()).value())
                .isEqualTo(50);
    }

    @Test
    void confidenceIgnoresNonActiveRecords() {
        List<IndicatorSource> records = List.of(
                new IndicatorSource(
                        report(SOURCE_A).confidence(Confidence.of(90)).build()),
                new IndicatorSource(report(SOURCE_B)
                        .confidence(Confidence.of(10))
                        .status(SourceRecordStatus.RETRACTED)
                        .build()));
        Map<SourceId, Reputation> reputations = Map.of(SOURCE_A, new Reputation(50), SOURCE_B, new Reputation(50));
        assertThat(IndicatorMergePolicy.aggregateConfidence(records, reputations)
                        .value())
                .isEqualTo(90);
    }

    @Test
    void confidenceBonusIsCappedAtOneHundred() {
        List<IndicatorSource> records = List.of(
                new IndicatorSource(
                        report(SOURCE_A).confidence(Confidence.of(98)).build()),
                new IndicatorSource(
                        report(SOURCE_B).confidence(Confidence.of(98)).build()),
                new IndicatorSource(report(com.ctip.testing.IndicatorTestBuilder.SOURCE_C)
                        .confidence(Confidence.of(98))
                        .build()));
        assertThat(IndicatorMergePolicy.aggregateConfidence(records, Map.of()).value())
                .isEqualTo(100);
    }

    @Test
    void fileHashWithoutExplicitValidUntilNeverExpires() {
        IndicatorSource record = new IndicatorSource(report(SOURCE_A).build());
        assertThat(record.effectiveValidUntil(IocType.FILE_HASH)).isNull();
        assertThat(IndicatorMergePolicy.aggregateValidUntil(List.of(record), IocType.FILE_HASH))
                .isNull();
    }

    @Test
    void allExpiredRecordsYieldExpiredStatus() {
        List<IndicatorSource> records = List.of(
                new IndicatorSource(
                        report(SOURCE_A).status(SourceRecordStatus.EXPIRED).build()),
                new IndicatorSource(
                        report(SOURCE_B).status(SourceRecordStatus.EXPIRED).build()));
        assertThat(IndicatorMergePolicy.determineStatus(records, Map.of())).isEqualTo(IndicatorStatus.EXPIRED);
    }

    @Test
    void untrustedRetractionWithActiveRecordsStaysActive() {
        List<IndicatorSource> records = List.of(
                new IndicatorSource(
                        report(SOURCE_A).status(SourceRecordStatus.RETRACTED).build()),
                new IndicatorSource(report(SOURCE_B).build()));
        Map<SourceId, Reputation> reputations = Map.of(SOURCE_A, new Reputation(79));
        assertThat(IndicatorMergePolicy.determineStatus(records, reputations)).isEqualTo(IndicatorStatus.ACTIVE);
    }
}
