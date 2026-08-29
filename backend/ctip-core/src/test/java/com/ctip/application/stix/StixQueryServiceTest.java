package com.ctip.application.stix;

import static com.ctip.testing.IndicatorTestBuilder.DEMO_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

import com.ctip.application.indicator.RedistributionFilter;
import com.ctip.domain.indicator.Indicator;
import com.ctip.domain.shared.Visibility;
import com.ctip.domain.stix.StixIndicatorProjector;
import com.ctip.domain.stix.StixRelationshipProjector;
import com.ctip.domain.stix.StixThreatProjector;
import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.threat.IndicatorRole;
import com.ctip.domain.threat.Threat;
import com.ctip.sdk.RedistributionPolicy;
import com.ctip.sdk.Tlp;
import com.ctip.testing.InMemoryIndicatorRepository;
import com.ctip.testing.InMemoryStixObjects;
import com.ctip.testing.InMemoryStixRelationships;
import com.ctip.testing.InMemoryThreatRepository;
import com.ctip.testing.IndicatorTestBuilder;
import com.ctip.testing.ThreatTestBuilder;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * GET /api/v1/stix/{stixId} 的查詢規則:marking 由常數供應;其餘物件的可見度依
 * <strong>來源 domain 物件</strong>判定(indicator / threat / 無來源),格式不合的 stixId 一律查無。
 */
@Tag("unit")
class StixQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-20T00:00:00Z");

    private final InMemoryIndicatorRepository indicators = new InMemoryIndicatorRepository();
    private final InMemoryThreatRepository threats = new InMemoryThreatRepository();
    private final InMemoryStixObjects stixObjects = new InMemoryStixObjects();
    private final InMemoryStixRelationships stixRelationships = new InMemoryStixRelationships();
    private final StixQueryService service =
            new StixQueryService(indicators, threats, stixObjects, stixRelationships, new RedistributionFilter());

    @Test
    void markingIsServedFromConstants() {
        assertThat(service.findMarking("marking-definition--94868c89-83c2-464b-929b-a1a8aa3c8487"))
                .hasValueSatisfying(m -> assertThat(m.get("name")).isEqualTo("TLP:CLEAR"));
        assertThat(service.findMarking("indicator--1f0d2c4e-93a5-4f6b-8c1d-2e3a4b5c6d7e"))
                .isEmpty();
    }

    @Test
    void visibleRedistributableIndicatorReturnsStoredContent() {
        Indicator indicator = storedIndicator(RedistributionPolicy.PUBLIC_REDISTRIBUTABLE);

        assertThat(service.findContent(StixIndicatorProjector.stixId(indicator.snapshot()), Visibility.anonymous()))
                .contains("{\"id\":\"indicator--" + indicator.id().value() + "\"}");
    }

    @Test
    void allInternalOnlyIndicatorIsHiddenFromNonOwner() {
        Indicator indicator = storedIndicator(RedistributionPolicy.INTERNAL_ONLY);
        String stixId = StixIndicatorProjector.stixId(indicator.snapshot());

        // 匿名雖綁 public tenant,仍屬公開輸出,不得豁免(§7.9 作用域修正的安全解讀;ADR 0006)
        assertThat(service.findContent(stixId, Visibility.anonymous())).isEmpty();
        // 非擁有租戶 → I14 隱藏
        assertThat(service.findContent(stixId, Visibility.authenticated(DEMO_TENANT)))
                .isEmpty();
    }

    @Test
    void threatProjectionFollowsThreatVisibility() {
        Threat threat = ThreatTestBuilder.malwareFamily(DEMO_TENANT, Tlp.AMBER);
        threats.save(threat);
        stixObjects.upsert(StixThreatProjector.project(threat.snapshot(), NOW, NOW));
        String stixId = StixThreatProjector.stixId(threat.snapshot());

        assertThat(service.findContent(stixId, Visibility.authenticated(DEMO_TENANT)))
                .isPresent();
        // 別的租戶 / 匿名看不到私有威脅
        assertThat(service.findContent(stixId, Visibility.anonymous())).isEmpty();
    }

    @Test
    void identityProjectionHasNoOriginAggregateAndIsPublic() {
        Indicator indicator = storedIndicator(RedistributionPolicy.PUBLIC_REDISTRIBUTABLE);
        // identity 的兩個來源欄都是 null(ck_so_origin 允許);TLP 固定 CLEAR
        stixObjects.upsert(new com.ctip.domain.stix.StixProjection(
                "identity--00000000-0000-0000-0000-0000000000a1",
                "identity",
                TenantId.PUBLIC,
                null,
                null,
                Tlp.CLEAR,
                NOW,
                NOW,
                Map.of("type", "identity")));
        assertThat(indicator).isNotNull();

        assertThat(service.findContent("identity--00000000-0000-0000-0000-0000000000a1", Visibility.anonymous()))
                .isPresent();
    }

    @Test
    void relationshipIsRebuiltFromTupleAndRequiresBothEndsVisible() {
        Indicator indicator = storedIndicator(RedistributionPolicy.PUBLIC_REDISTRIBUTABLE);
        Threat threat = ThreatTestBuilder.malwareFamily(TenantId.PUBLIC, Tlp.CLEAR);
        threat.linkIndicator(indicator.id(), IndicatorRole.C2, NOW);
        threats.save(threat);
        stixRelationships.syncForTarget(
                StixThreatProjector.stixId(threat.snapshot()),
                java.util.List.of(StixRelationshipProjector.project(
                        threat.snapshot(), threat.indicators().getFirst(), NOW, NOW)));
        String stixId = stixRelationships.all().getFirst().stixId();

        assertThat(service.findRelationship(stixId, Visibility.anonymous())).hasValueSatisfying(content -> {
            assertThat(content.get("relationship_type")).isEqualTo("indicates");
            assertThat(content.get("source_ref"))
                    .isEqualTo("indicator--" + indicator.id().value());
            assertThat(content.get("description")).isEqualTo("Indicator role within the threat: C2");
        });
        // stix_objects 那條路徑不處理 relationship(表 9 是另一張表)
        assertThat(service.findContent(stixId, Visibility.anonymous())).isEmpty();
    }

    @Test
    void malformedStixIdsAreNotFound() {
        assertThat(service.findContent("indicator--not-a-uuid", Visibility.anonymous()))
                .isEmpty();
        assertThat(service.findContent("threat--1f0d2c4e-93a5-4f6b-8c1d-2e3a4b5c6d7e", Visibility.anonymous()))
                .isEmpty();
        assertThat(service.findContent(null, Visibility.anonymous())).isEmpty();
        assertThat(service.findRelationship("relationship--not-a-uuid", Visibility.anonymous()))
                .isEmpty();
    }

    private Indicator storedIndicator(RedistributionPolicy policy) {
        Indicator indicator = IndicatorTestBuilder.activeIndicator(TenantId.PUBLIC, Tlp.CLEAR, policy);
        indicators.save(indicator);
        stixObjects.upsert(StixIndicatorProjector.project(indicator.snapshot(), Map.of(), NOW, NOW));
        return indicator;
    }
}
