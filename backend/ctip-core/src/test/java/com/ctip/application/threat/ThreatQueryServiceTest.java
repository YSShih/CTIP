package com.ctip.application.threat;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctip.domain.indicator.Indicator;
import com.ctip.domain.shared.Visibility;
import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.threat.IndicatorRole;
import com.ctip.domain.threat.Threat;
import com.ctip.domain.threat.ThreatId;
import com.ctip.sdk.RedistributionPolicy;
import com.ctip.sdk.Tlp;
import com.ctip.testing.InMemoryIndicatorRepository;
import com.ctip.testing.InMemoryThreatRepository;
import com.ctip.testing.IndicatorTestBuilder;
import com.ctip.testing.ThreatTestBuilder;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Threat 讀取編排的兩段可見度(07 §7.7):Threat 自身,以及<strong>關聯 IOC 各自再走一次</strong>
 * ——關聯不是可見度的旁路。
 */
@Tag("unit")
class ThreatQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-20T00:00:00Z");
    private static final TenantId OWNER = IndicatorTestBuilder.DEMO_TENANT;

    private final InMemoryThreatRepository threats = new InMemoryThreatRepository();
    private final InMemoryIndicatorRepository indicators = new InMemoryIndicatorRepository();
    private final ThreatQueryService service = new ThreatQueryService(threats, indicators);

    @Test
    void listAndByIdApplyTheThreatVisibilityPredicate() {
        threats.save(ThreatTestBuilder.malwareFamily(TenantId.PUBLIC, Tlp.CLEAR));
        Threat privateThreat = ThreatTestBuilder.threat(
                new ThreatId(UUID.fromString("00000000-0000-0000-0000-0000000000c1")),
                OWNER,
                com.ctip.domain.threat.ThreatType.CAMPAIGN,
                "Operation Private",
                Tlp.AMBER);
        threats.save(privateThreat);

        assertThat(service.list(ThreatFilter.none(), Visibility.anonymous(), null, 10)
                        .items())
                .singleElement()
                .satisfies(threat -> assertThat(threat.name()).isEqualTo("AgentTesla"));
        assertThat(service.list(ThreatFilter.none(), Visibility.authenticated(OWNER), null, 10)
                        .items())
                .hasSize(2);
        assertThat(service.byId(privateThreat.id(), Visibility.anonymous())).isEmpty();
        assertThat(service.byId(privateThreat.id(), Visibility.authenticated(OWNER)))
                .isPresent();
    }

    @Test
    void linkedIndicatorsOmitTheOnesTheViewerCannotSee() {
        Threat threat = ThreatTestBuilder.malwareFamily(TenantId.PUBLIC, Tlp.CLEAR);
        Indicator visible = indicators.save(IndicatorTestBuilder.activeIndicator(
                TenantId.PUBLIC, Tlp.CLEAR, RedistributionPolicy.PUBLIC_REDISTRIBUTABLE));
        threat.linkIndicator(visible.id(), IndicatorRole.C2, NOW);
        // 只存在於關聯中的 IOC(查無)一樣不得出現,且不得讓整個查詢失敗
        threat.linkIndicator(
                new com.ctip.domain.indicator.IndicatorId(UUID.fromString("00000000-0000-0000-0000-0000000000f1")),
                IndicatorRole.PAYLOAD,
                NOW.plusSeconds(60));
        threats.save(threat);

        assertThat(service.linkedIndicators(threat, Visibility.anonymous()))
                .singleElement()
                .satisfies(link -> {
                    assertThat(link.indicator().id()).isEqualTo(visible.id());
                    assertThat(link.role()).isEqualTo(IndicatorRole.C2);
                    assertThat(link.addedAt()).isEqualTo(NOW);
                });
    }

    @Test
    void aThreatWithoutLinksNeedsNoIndicatorQuery() {
        Threat threat = ThreatTestBuilder.malwareFamily(TenantId.PUBLIC, Tlp.CLEAR);

        assertThat(service.linkedIndicators(threat, Visibility.anonymous())).isEmpty();
    }
}
