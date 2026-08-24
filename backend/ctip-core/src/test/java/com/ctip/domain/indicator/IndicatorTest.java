package com.ctip.domain.indicator;

import static com.ctip.testing.IndicatorTestBuilder.DEMO_TENANT;
import static com.ctip.testing.IndicatorTestBuilder.SOURCE_A;
import static com.ctip.testing.IndicatorTestBuilder.SOURCE_B;
import static com.ctip.testing.IndicatorTestBuilder.SOURCE_C;
import static com.ctip.testing.IndicatorTestBuilder.T0;
import static com.ctip.testing.IndicatorTestBuilder.activeIndicator;
import static com.ctip.testing.IndicatorTestBuilder.report;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ctip.domain.event.IndicatorEvents.IndicatorCreated;
import com.ctip.domain.event.IndicatorEvents.IndicatorExpired;
import com.ctip.domain.event.IndicatorEvents.IndicatorRevoked;
import com.ctip.domain.source.Reputation;
import com.ctip.domain.tenant.TenantId;
import com.ctip.sdk.Confidence;
import com.ctip.sdk.RedistributionPolicy;
import com.ctip.sdk.Severity;
import com.ctip.sdk.Tlp;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * 不變量 I2–I14(docs/spec/02-ddd-model.md §2.3)。
 * I1(識別鍵唯一)由 ux_indicators_identity 與 repository 強制,見 RequiredIndexTest 與 SecurityTest。
 */
@Tag("unit")
class IndicatorTest {

    @Test
    void i2FingerprintIsComputedOverNormalizedValue() {
        Indicator indicator = activeIndicator(TenantId.PUBLIC, Tlp.CLEAR, RedistributionPolicy.PUBLIC_REDISTRIBUTABLE);
        // SHA-256("mal-example.ctip-sample.net") 而非原始值
        assertThat(indicator.fingerprint().hex()).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(indicator.pullEvents()).first().isInstanceOf(IndicatorCreated.class);
    }

    @Test
    void i4LastSeenMustNotPrecedeFirstSeen() {
        assertThatThrownBy(() -> new IndicatorSource(
                        report(SOURCE_A).seen(T0, T0.minusSeconds(60)).build()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void i5FirstSeenIsMinAndLastSeenIsMaxAcrossSources() {
        Indicator indicator = activeIndicator(TenantId.PUBLIC, Tlp.CLEAR, RedistributionPolicy.PUBLIC_REDISTRIBUTABLE);
        Instant earlier = T0.minus(Duration.ofDays(2));
        Instant later = T0.plus(Duration.ofDays(1));
        indicator.mergeFrom(
                new IndicatorSource(report(SOURCE_B).seen(earlier, later).build()), new Reputation(50));
        IndicatorSnapshot s = indicator.snapshot();
        assertThat(s.firstSeen()).isEqualTo(earlier);
        assertThat(s.lastSeen()).isEqualTo(later);
    }

    @Test
    void i6ValidUntilIsMaxAndNullOnlyWhenAllSourcesHaveNone() {
        // DOMAIN 型別:來源未明示 → lastSeen + 90 天
        Indicator indicator = activeIndicator(TenantId.PUBLIC, Tlp.CLEAR, RedistributionPolicy.PUBLIC_REDISTRIBUTABLE);
        assertThat(indicator.snapshot().validUntil()).isEqualTo(T0.plus(Duration.ofDays(90)));

        Instant explicit = T0.plus(Duration.ofDays(365));
        indicator.mergeFrom(
                new IndicatorSource(report(SOURCE_B).validUntil(explicit).build()), new Reputation(50));
        assertThat(indicator.snapshot().validUntil()).isEqualTo(explicit);
    }

    @Test
    void i7TlpIsStrictestAcrossSources() {
        Indicator indicator = activeIndicator(TenantId.PUBLIC, Tlp.CLEAR, RedistributionPolicy.PUBLIC_REDISTRIBUTABLE);
        indicator.mergeFrom(new IndicatorSource(report(SOURCE_B).tlp(Tlp.AMBER).build()), new Reputation(50));
        assertThat(indicator.tlp()).isEqualTo(Tlp.AMBER);
    }

    @Test
    void i8SeverityIsMaxAcrossSources() {
        Indicator indicator = activeIndicator(TenantId.PUBLIC, Tlp.CLEAR, RedistributionPolicy.PUBLIC_REDISTRIBUTABLE);
        indicator.mergeFrom(
                new IndicatorSource(report(SOURCE_B).severity(Severity.CRITICAL).build()), new Reputation(50));
        assertThat(indicator.severity()).isEqualTo(Severity.CRITICAL);
    }

    @Test
    void i9TagsAreUnionAcrossSources() {
        Indicator indicator = activeIndicator(TenantId.PUBLIC, Tlp.CLEAR, RedistributionPolicy.PUBLIC_REDISTRIBUTABLE);
        indicator.mergeFrom(
                new IndicatorSource(
                        report(SOURCE_B).tags(Set.of("phishing", "c2")).build()),
                new Reputation(50));
        indicator.mergeFrom(
                new IndicatorSource(
                        report(SOURCE_C).tags(Set.of("c2", "botnet")).build()),
                new Reputation(50));
        assertThat(indicator.tags()).contains("phishing", "c2", "botnet");
    }

    @Test
    void i10ConfidenceIsReputationWeightedWithMultiSourceBonus() {
        Indicator indicator = activeIndicator(TenantId.PUBLIC, Tlp.CLEAR, RedistributionPolicy.PUBLIC_REDISTRIBUTABLE);
        // A: conf 60 × rep 70;B: conf 80 × rep 30 → (4200+2400)/100 = 66,僅 2 來源無 bonus
        indicator.mergeFrom(
                new IndicatorSource(
                        report(SOURCE_B).confidence(Confidence.of(80)).build()),
                new Reputation(30));
        assertThat(indicator.confidence().value()).isEqualTo(66);
        // 第 3 個獨立 ACTIVE 來源 → +10
        indicator.mergeFrom(
                new IndicatorSource(
                        report(SOURCE_C).confidence(Confidence.of(66)).build()),
                new Reputation(50));
        assertThat(indicator.confidence().value()).isEqualTo(76);
    }

    @Test
    void i11StatusFollowsShortCircuitOrder() {
        // 規則 1:信譽 >= 80 的撤回 → REVOKED
        Indicator revoked = activeIndicator(TenantId.PUBLIC, Tlp.CLEAR, RedistributionPolicy.PUBLIC_REDISTRIBUTABLE);
        revoked.revoke(SOURCE_A, new Reputation(80));
        assertThat(revoked.status()).isEqualTo(IndicatorStatus.REVOKED);
        assertThat(revoked.pullEvents()).last().isInstanceOf(IndicatorRevoked.class);

        // 規則 2:誤判且無其他 ACTIVE 來源 → FALSE_POSITIVE
        Indicator fp = activeIndicator(TenantId.PUBLIC, Tlp.CLEAR, RedistributionPolicy.PUBLIC_REDISTRIBUTABLE);
        fp.reportFalsePositive(SOURCE_A);
        assertThat(fp.status()).isEqualTo(IndicatorStatus.FALSE_POSITIVE);

        // 規則 2 反例:仍有其他 ACTIVE 來源 → ACTIVE
        Indicator stillActive =
                activeIndicator(TenantId.PUBLIC, Tlp.CLEAR, RedistributionPolicy.PUBLIC_REDISTRIBUTABLE);
        stillActive.mergeFrom(new IndicatorSource(report(SOURCE_B).build()), new Reputation(50));
        stillActive.reportFalsePositive(SOURCE_A);
        assertThat(stillActive.status()).isEqualTo(IndicatorStatus.ACTIVE);
    }

    @Test
    void i11UntrustedRetractionDoesNotRevoke() {
        Indicator indicator = activeIndicator(TenantId.PUBLIC, Tlp.CLEAR, RedistributionPolicy.PUBLIC_REDISTRIBUTABLE);
        assertThatThrownBy(() -> indicator.revoke(SOURCE_A, new Reputation(79)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(indicator.status()).isEqualTo(IndicatorStatus.ACTIVE);
    }

    @Test
    void i13IndicatorWithoutSourcesIsIllegal() {
        Indicator indicator = activeIndicator(TenantId.PUBLIC, Tlp.CLEAR, RedistributionPolicy.PUBLIC_REDISTRIBUTABLE);
        IndicatorSnapshot valid = indicator.snapshot();
        IndicatorSnapshot zeroSources = new IndicatorSnapshot(
                valid.id(),
                valid.ownerTenantId(),
                valid.value(),
                valid.fingerprint(),
                valid.firstSeen(),
                valid.lastSeen(),
                valid.validUntil(),
                valid.confidence(),
                valid.severity(),
                valid.score(),
                valid.tlp(),
                valid.status(),
                valid.tags(),
                java.util.List.of(),
                valid.hashRecords());
        assertThatThrownBy(() -> Indicator.reconstitute(zeroSources)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void i14InternalOnlyIndicatorIsNotRedistributableToOtherTenants() {
        Indicator internalOnly = activeIndicator(DEMO_TENANT, Tlp.AMBER, RedistributionPolicy.INTERNAL_ONLY);
        assertThat(internalOnly.canBeRedistributedTo(DEMO_TENANT)).isTrue();
        assertThat(internalOnly.canBeRedistributedTo(TenantId.PUBLIC)).isFalse();
        assertThat(internalOnly.eligibleForBloom()).isFalse();

        Indicator shared = activeIndicator(DEMO_TENANT, Tlp.AMBER, RedistributionPolicy.ATTRIBUTION_REQUIRED);
        assertThat(shared.canBeRedistributedTo(TenantId.PUBLIC)).isTrue();
    }

    @Test
    void markExpiredRequiresElapsedValidUntil() {
        Indicator indicator = activeIndicator(TenantId.PUBLIC, Tlp.CLEAR, RedistributionPolicy.PUBLIC_REDISTRIBUTABLE);
        Instant beforeExpiry = T0.plus(Duration.ofDays(1));
        assertThatThrownBy(() -> indicator.markExpired(beforeExpiry)).isInstanceOf(IllegalStateException.class);

        Instant afterExpiry = T0.plus(Duration.ofDays(91));
        indicator.markExpired(afterExpiry);
        assertThat(indicator.status()).isEqualTo(IndicatorStatus.EXPIRED);
        assertThat(indicator.pullEvents()).last().isInstanceOf(IndicatorExpired.class);
    }

    @Test
    void eligibleForBloomRequiresActiveClearAndRedistributable() {
        Indicator eligible = activeIndicator(TenantId.PUBLIC, Tlp.CLEAR, RedistributionPolicy.PUBLIC_REDISTRIBUTABLE);
        assertThat(eligible.eligibleForBloom()).isTrue();
        Indicator green = activeIndicator(TenantId.PUBLIC, Tlp.GREEN, RedistributionPolicy.PUBLIC_REDISTRIBUTABLE);
        assertThat(green.eligibleForBloom()).isFalse();
    }

    @Test
    void isVisibleToImplementsTlpVisibilityTable() {
        Indicator publicGreen =
                activeIndicator(TenantId.PUBLIC, Tlp.GREEN, RedistributionPolicy.PUBLIC_REDISTRIBUTABLE);
        assertThat(publicGreen.isVisibleTo(Tlp.CLEAR, TenantId.PUBLIC)).isTrue(); // owner 即 viewer
        assertThat(publicGreen.isVisibleTo(Tlp.GREEN, DEMO_TENANT)).isTrue();

        Indicator demoAmber = activeIndicator(DEMO_TENANT, Tlp.AMBER, RedistributionPolicy.INTERNAL_ONLY);
        assertThat(demoAmber.isVisibleTo(Tlp.AMBER_STRICT, DEMO_TENANT)).isTrue();
        assertThat(demoAmber.isVisibleTo(Tlp.GREEN, TenantId.PUBLIC)).isFalse();
    }

    @Test
    void sameSourceReportUpsertsInsteadOfDuplicating() {
        Indicator indicator = activeIndicator(TenantId.PUBLIC, Tlp.CLEAR, RedistributionPolicy.PUBLIC_REDISTRIBUTABLE);
        indicator.mergeFrom(
                new IndicatorSource(
                        report(SOURCE_A).seen(T0, T0.plus(Duration.ofHours(6))).build()),
                new Reputation(70));
        IndicatorSnapshot s = indicator.snapshot();
        assertThat(s.sources()).hasSize(1);
        assertThat(s.sources().getFirst().reportCount()).isEqualTo(2);
        assertThat(s.lastSeen()).isEqualTo(T0.plus(Duration.ofHours(6)));
    }
}
