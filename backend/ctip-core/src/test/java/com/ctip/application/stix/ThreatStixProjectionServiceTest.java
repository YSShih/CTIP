package com.ctip.application.stix;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctip.domain.indicator.IndicatorId;
import com.ctip.domain.stix.StixThreatProjector;
import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.threat.IndicatorRole;
import com.ctip.domain.threat.Threat;
import com.ctip.domain.threat.ThreatType;
import com.ctip.sdk.Tlp;
import com.ctip.testing.FixedClockPort;
import com.ctip.testing.InMemoryStixObjects;
import com.ctip.testing.InMemoryStixRelationships;
import com.ctip.testing.InMemoryThreatRepository;
import com.ctip.testing.ThreatTestBuilder;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Threat 的 STIX 投影(§7.8.1 的 M2 範圍、§7.8.6 的失敗隔離):
 * 只投影 MALWARE_FAMILY / ATTACK_PATTERN;關聯以 target 為單位同步,解除即消失。
 */
@Tag("unit")
class ThreatStixProjectionServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-20T00:00:00Z");
    private static final IndicatorId IOC_A = new IndicatorId(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final IndicatorId IOC_B = new IndicatorId(UUID.fromString("00000000-0000-0000-0000-0000000000b2"));

    private final InMemoryThreatRepository threats = new InMemoryThreatRepository();
    private final InMemoryStixObjects stixObjects = new InMemoryStixObjects();
    private final InMemoryStixRelationships relationships = new InMemoryStixRelationships();
    private final ThreatStixProjectionService service =
            new ThreatStixProjectionService(threats, stixObjects, relationships, new FixedClockPort(NOW));

    @Test
    void malwareFamilyIsProjectedWithOneRelationshipPerLink() {
        Threat threat = ThreatTestBuilder.malwareFamily(TenantId.PUBLIC, Tlp.CLEAR);
        threat.linkIndicator(IOC_A, IndicatorRole.C2, NOW);
        threat.linkIndicator(IOC_B, IndicatorRole.PAYLOAD, NOW);
        threats.save(threat);

        service.project(threat.id());

        assertThat(stixObjects.ofType("malware")).singleElement().satisfies(projection -> {
            assertThat(projection.stixId()).isEqualTo(StixThreatProjector.stixId(threat.snapshot()));
            assertThat(projection.threatId()).isEqualTo(threat.id());
            assertThat(projection.indicatorId()).isNull();
        });
        assertThat(relationships.all())
                .hasSize(2)
                .allSatisfy(relationship ->
                        assertThat(relationship.relationshipType()).isEqualTo("indicates"));
    }

    @Test
    void unlinkingRemovesTheRelationshipProjection() {
        Threat threat = ThreatTestBuilder.malwareFamily(TenantId.PUBLIC, Tlp.CLEAR);
        threat.linkIndicator(IOC_A, IndicatorRole.C2, NOW);
        threat.linkIndicator(IOC_B, IndicatorRole.PAYLOAD, NOW);
        threats.save(threat);
        service.project(threat.id());

        threat.unlinkIndicator(IOC_B);
        threats.save(threat);
        service.project(threat.id());

        assertThat(relationships.all())
                .singleElement()
                .satisfies(
                        relationship -> assertThat(relationship.sourceRef()).isEqualTo("indicator--" + IOC_A.value()));
    }

    @Test
    void createdIsPreservedAcrossReprojections() {
        Threat threat = ThreatTestBuilder.malwareFamily(TenantId.PUBLIC, Tlp.CLEAR);
        threats.save(threat);
        service.project(threat.id());
        Instant firstCreated = stixObjects.ofType("malware").getFirst().created();

        service.project(threat.id());

        assertThat(stixObjects.ofType("malware").getFirst().created()).isEqualTo(firstCreated);
    }

    @Test
    void typesWithoutAnSdoAreNotProjectedAtAll() {
        Threat campaign = ThreatTestBuilder.threat(
                ThreatTestBuilder.THREAT_ID, TenantId.PUBLIC, ThreatType.CAMPAIGN, "Operation X", Tlp.CLEAR);
        campaign.linkIndicator(IOC_A, IndicatorRole.C2, NOW);
        threats.save(campaign);

        service.project(campaign.id());

        assertThat(stixObjects.all()).isEmpty();
        assertThat(relationships.all()).isEmpty();
    }

    @Test
    void aMissingThreatIsSilentlyIgnored() {
        service.project(ThreatTestBuilder.THREAT_ID);

        assertThat(stixObjects.all()).isEmpty();
    }
}
