package com.ctip.domain.threat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ctip.domain.event.ThreatEvents.ThreatUpdated;
import com.ctip.domain.indicator.IndicatorId;
import com.ctip.domain.tenant.TenantId;
import com.ctip.sdk.Confidence;
import com.ctip.sdk.Severity;
import com.ctip.sdk.Tlp;
import com.ctip.testing.ThreatTestBuilder;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Threat 聚合的不變量 H2–H5 與行為(docs/spec/02-ddd-model.md §2.3)。 */
@Tag("unit")
class ThreatTest {

    private static final Instant T0 = ThreatTestBuilder.T0;
    private static final IndicatorId IOC_A = new IndicatorId(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final IndicatorId IOC_B = new IndicatorId(UUID.fromString("00000000-0000-0000-0000-0000000000b2"));

    @Test
    void h2LastSeenMustNotPrecedeFirstSeen() {
        assertThatThrownBy(() -> Threat.reconstitute(snapshotWith(T0, T0.minusSeconds(1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("H2");
    }

    @Test
    void h3ExternalReferenceNeedsAnExternalIdOrUrl() {
        assertThatThrownBy(() -> new ExternalReference("mitre-attack", null, null, "no target"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("H3");
        assertThat(new ExternalReference("mitre-attack", "T1566", null, null).identityKey())
                .isEqualTo("mitre-attack T1566");
        // null 與空字串同義,與 DB 的 COALESCE(external_id, '') 唯一索引一致
        assertThat(new ExternalReference("cve", "", "https://example.test", null).identityKey())
                .isEqualTo("cve ");
    }

    @Test
    void h4RejectsDuplicateSourceNameAndExternalIdWithinOneThreat() {
        Threat threat = ThreatTestBuilder.malwareFamily(TenantId.PUBLIC, Tlp.CLEAR);
        threat.addExternalReference(new ExternalReference("mitre-attack", "S0331", null, null));

        assertThatThrownBy(() -> threat.addExternalReference(
                        new ExternalReference("mitre-attack", "S0331", "https://x.test", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("H4");
        // 同來源、不同 externalId 是允許的
        threat.addExternalReference(new ExternalReference("mitre-attack", "T1566", null, null));
        assertThat(threat.externalReferences()).hasSize(2);
    }

    @Test
    void h5LinkStoresOnlyTheIndicatorIdAndUpdatesRoleInPlace() {
        Threat threat = ThreatTestBuilder.malwareFamily(TenantId.PUBLIC, Tlp.CLEAR);
        Instant linkedAt = T0.plusSeconds(3600);

        threat.linkIndicator(IOC_A, IndicatorRole.UNKNOWN, linkedAt);
        threat.linkIndicator(IOC_A, IndicatorRole.C2, linkedAt.plusSeconds(60));

        assertThat(threat.indicators()).hasSize(1);
        ThreatIndicatorLink link = threat.indicators().getFirst();
        assertThat(link.indicatorId()).isEqualTo(IOC_A);
        assertThat(link.role()).isEqualTo(IndicatorRole.C2);
        // 角色變更不重設 addedAt(關聯的識別是 indicatorId)
        assertThat(link.addedAt()).isEqualTo(linkedAt);
    }

    @Test
    void linkingAdvancesLastSeenButNeverMovesItBackwards() {
        Threat threat = ThreatTestBuilder.malwareFamily(TenantId.PUBLIC, Tlp.CLEAR);
        threat.linkIndicator(IOC_A, IndicatorRole.C2, T0.plusSeconds(7200));
        threat.linkIndicator(IOC_B, IndicatorRole.PAYLOAD, T0.plusSeconds(60));

        assertThat(threat.snapshot().lastSeen()).isEqualTo(T0.plusSeconds(7200));
    }

    @Test
    void unlinkReportsWhetherTheLinkExisted() {
        Threat threat = ThreatTestBuilder.malwareFamily(TenantId.PUBLIC, Tlp.CLEAR);
        threat.linkIndicator(IOC_A, IndicatorRole.C2, T0);

        assertThat(threat.unlinkIndicator(IOC_B)).isFalse();
        assertThat(threat.unlinkIndicator(IOC_A)).isTrue();
        assertThat(threat.indicators()).isEmpty();
    }

    @Test
    void tlpTighteningIsOneWay() {
        Threat threat = ThreatTestBuilder.malwareFamily(TenantId.PUBLIC, Tlp.GREEN);

        assertThat(threat.tightenTlpTo(Tlp.CLEAR)).isFalse();
        assertThat(threat.tlp()).isEqualTo(Tlp.GREEN);
        assertThat(threat.tightenTlpTo(Tlp.AMBER)).isTrue();
        assertThat(threat.tlp()).isEqualTo(Tlp.AMBER);
    }

    @Test
    void retiredIsTerminalAndBlocksFurtherChanges() {
        Threat threat = ThreatTestBuilder.malwareFamily(TenantId.PUBLIC, Tlp.CLEAR);
        threat.changeStatus(ThreatStatus.DORMANT);
        threat.retire();

        assertThat(threat.status()).isEqualTo(ThreatStatus.RETIRED);
        assertThatThrownBy(() -> threat.changeStatus(ThreatStatus.ACTIVE)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> threat.linkIndicator(IOC_A, IndicatorRole.C2, T0))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> threat.addExternalReference(new ExternalReference("cve", "CVE-2026-1", null, null)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void settingTheStatusItAlreadyHasIsRejected() {
        Threat threat = ThreatTestBuilder.malwareFamily(TenantId.PUBLIC, Tlp.CLEAR);

        assertThatThrownBy(() -> threat.changeStatus(ThreatStatus.ACTIVE)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void everyMutationRecordsAThreatUpdatedEventWithItsChangeKind() {
        Threat threat = ThreatTestBuilder.malwareFamily(TenantId.PUBLIC, Tlp.CLEAR);
        threat.linkIndicator(IOC_A, IndicatorRole.C2, T0);
        threat.tightenTlpTo(Tlp.AMBER);
        threat.addExternalReference(new ExternalReference("cve", "CVE-2026-1", null, null));
        threat.unlinkIndicator(IOC_A);
        threat.retire();

        assertThat(threat.pullEvents())
                .extracting(event -> ((ThreatUpdated) event).change())
                .containsExactly(
                        ThreatChange.CREATED,
                        ThreatChange.INDICATOR_LINKED,
                        ThreatChange.TLP_TIGHTENED,
                        ThreatChange.EXTERNAL_REFERENCE_ADDED,
                        ThreatChange.INDICATOR_UNLINKED,
                        ThreatChange.STATUS_CHANGED);
        assertThat(threat.pullEvents()).isEmpty();
    }

    @Test
    void onlyMalwareFamilyAndAttackPatternHaveAStixProjection() {
        assertThat(ThreatTestBuilder.malwareFamily(TenantId.PUBLIC, Tlp.CLEAR).hasStixProjection())
                .isTrue();
        assertThat(ThreatTestBuilder.threat(
                                ThreatTestBuilder.THREAT_ID,
                                TenantId.PUBLIC,
                                ThreatType.ATTACK_PATTERN,
                                "Phishing",
                                Tlp.CLEAR)
                        .hasStixProjection())
                .isTrue();
        assertThat(ThreatTestBuilder.threat(
                                ThreatTestBuilder.THREAT_ID, TenantId.PUBLIC, ThreatType.CAMPAIGN, "Op X", Tlp.CLEAR)
                        .hasStixProjection())
                .isFalse();
    }

    @Test
    void blankNameIsRejectedBecauseItIsPartOfTheIdentityKey() {
        assertThatThrownBy(() -> ThreatTestBuilder.threat(
                        ThreatTestBuilder.THREAT_ID, TenantId.PUBLIC, ThreatType.CAMPAIGN, "   ", Tlp.CLEAR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("H1");
    }

    private static ThreatSnapshot snapshotWith(Instant firstSeen, Instant lastSeen) {
        return new ThreatSnapshot(
                ThreatTestBuilder.THREAT_ID,
                TenantId.PUBLIC,
                ThreatType.CAMPAIGN,
                "Operation X",
                Set.of(),
                null,
                Severity.LOW,
                Confidence.of(10),
                Tlp.CLEAR,
                ThreatStatus.ACTIVE,
                firstSeen,
                lastSeen,
                Set.of(),
                List.of(),
                List.of());
    }
}
