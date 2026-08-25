package com.ctip.domain.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ctip.domain.event.SourceEvents.SourceDegraded;
import com.ctip.domain.event.SourceEvents.SourceFailed;
import com.ctip.domain.event.SourceEvents.SourceRecovered;
import com.ctip.sdk.RedistributionPolicy;
import com.ctip.sdk.SourceType;
import com.ctip.sdk.Tlp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** 不變量 S1–S5(docs/spec/02-ddd-model.md §2.3;S6 屬 adapter 設定層,Phase 5)。 */
@Tag("unit")
class SourceTest {

    private static final Instant NOW = Instant.parse("2026-08-21T12:00:00Z");

    @Test
    void s1ReputationMustBeWithinRange() {
        assertThat(new Reputation(0).value()).isZero();
        assertThat(new Reputation(100).value()).isEqualTo(100);
        assertThatThrownBy(() -> new Reputation(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Reputation(101)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new Reputation(80).isTrustedForRetraction()).isTrue();
        assertThat(new Reputation(79).isTrustedForRetraction()).isFalse();
    }

    @Test
    void s2ThreeFailuresDegradeTenFailuresFailAndSuccessRecovers() {
        Source source = syncableSource();
        for (int i = 0; i < 2; i++) {
            source.recordFailure("boom", NOW);
        }
        assertThat(source.health().status()).isEqualTo(SourceStatus.ACTIVE);
        source.recordFailure("boom", NOW);
        assertThat(source.health().status()).isEqualTo(SourceStatus.DEGRADED);
        assertThat(source.pullEvents()).hasSize(1).first().isInstanceOf(SourceDegraded.class);

        for (int i = 3; i < 10; i++) {
            source.recordFailure("boom", NOW);
        }
        assertThat(source.health().status()).isEqualTo(SourceStatus.FAILED);
        assertThat(source.health().consecutiveFailures()).isEqualTo(10);
        assertThat(source.pullEvents()).last().isInstanceOf(SourceFailed.class);

        source.recordSuccess(5, Duration.ofMillis(120), NOW);
        assertThat(source.health().status()).isEqualTo(SourceStatus.ACTIVE);
        assertThat(source.health().consecutiveFailures()).isZero();
        assertThat(source.pullEvents()).hasSize(1).first().isInstanceOf(SourceRecovered.class);
    }

    @Test
    void s3DisabledIsManualOnlyAndSurvivesAutomaticTransitions() {
        Source source = syncableSource();
        source.disable();
        assertThat(source.health().status()).isEqualTo(SourceStatus.DISABLED);
        assertThat(source.enabled()).isFalse();

        source.recordSuccess(1, Duration.ofMillis(50), NOW);
        assertThat(source.health().status()).isEqualTo(SourceStatus.DISABLED);
        source.recordFailure("boom", NOW);
        assertThat(source.health().status()).isEqualTo(SourceStatus.DISABLED);

        source.enable();
        assertThat(source.health().status()).isEqualTo(SourceStatus.ACTIVE);
        assertThat(source.health().consecutiveFailures()).isZero();
    }

    @Test
    void s4NonSyncableSourceDoesNotParticipateInStateMachine() {
        Source manual = Source.reconstitute(new SourceSnapshot(
                new SourceId(UUID.fromString("00000000-0000-0000-0000-0000000000aa")),
                SourceType.MANUAL,
                "Manual",
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
        assertThatThrownBy(() -> manual.recordFailure("x", NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> manual.recordSuccess(1, Duration.ZERO, NOW)).isInstanceOf(IllegalStateException.class);
        assertThat(manual.health().status()).isEqualTo(SourceStatus.ACTIVE);
        assertThat(manual.isDueForSync(NOW)).isFalse();
    }

    @Test
    void s5FailureMessagesAreMaskedBeforeStorage() {
        Source source = syncableSource();
        source.recordFailure("call failed: api_key=SUPERSECRET123 Authorization: Bearer abc.def.ghi", NOW);
        assertThat(source.lastErrorMessage()).doesNotContain("SUPERSECRET123").doesNotContain("abc.def.ghi");
        assertThat(source.lastErrorMessage()).contains("api_key=***");
    }

    @Test
    void isDueForSyncHonoursIntervalAndDisabledState() {
        Source source = syncableSource();
        assertThat(source.isDueForSync(NOW)).isTrue();
        source.recordSuccess(1, Duration.ofMillis(10), NOW);
        assertThat(source.isDueForSync(NOW.plus(Duration.ofMinutes(30)))).isFalse();
        assertThat(source.isDueForSync(NOW.plus(Duration.ofHours(1)))).isTrue();
        source.disable();
        assertThat(source.isDueForSync(NOW.plus(Duration.ofDays(1)))).isFalse();
    }

    private static Source syncableSource() {
        return Source.reconstitute(new SourceSnapshot(
                new SourceId(UUID.fromString("00000000-0000-0000-0000-0000000000bb")),
                SourceType.MOCK_OPENPHISH,
                "Mock OpenPhish",
                Tlp.CLEAR,
                RedistributionPolicy.ATTRIBUTION_REQUIRED,
                new Reputation(70),
                true,
                true,
                Duration.ofHours(1),
                SourceHealth.initial(),
                null,
                null,
                0));
    }
}
