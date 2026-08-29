package com.ctip.application.threat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ctip.application.identity.AuthenticatedIdentity;
import com.ctip.application.ingestion.PublishNotPermittedException;
import com.ctip.application.rbac.RoleCode;
import com.ctip.domain.event.IndicatorEvents.IndicatorTlpTightened;
import com.ctip.domain.indicator.Indicator;
import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.threat.ExternalReference;
import com.ctip.domain.threat.IndicatorRole;
import com.ctip.domain.threat.Threat;
import com.ctip.domain.threat.ThreatId;
import com.ctip.domain.threat.ThreatStatus;
import com.ctip.domain.threat.ThreatType;
import com.ctip.domain.user.UserId;
import com.ctip.sdk.RedistributionPolicy;
import com.ctip.sdk.Severity;
import com.ctip.sdk.Tlp;
import com.ctip.testing.FixedClockPort;
import com.ctip.testing.InMemoryIndicatorRepository;
import com.ctip.testing.InMemoryThreatRepository;
import com.ctip.testing.IndicatorTestBuilder;
import com.ctip.testing.RecordingEventPublisher;
import com.ctip.testing.SequentialIdGenerator;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Threat 寫入編排,重點在 <strong>H6</strong>(§2.3 已降格為應用層一致性規則;ADR 0020):
 * 建立關聯時收緊、Indicator 事後收緊時連帶收緊,且永不放寬。
 */
@Tag("unit")
class ThreatServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-20T00:00:00Z");
    private static final TenantId OWNER = IndicatorTestBuilder.DEMO_TENANT;
    private static final TenantId STRANGER = new TenantId(UUID.fromString("00000000-0000-0000-0000-00000000dead"));

    private final InMemoryThreatRepository threats = new InMemoryThreatRepository();
    private final InMemoryIndicatorRepository indicators = new InMemoryIndicatorRepository();
    private final RecordingEventPublisher events = new RecordingEventPublisher();
    private final ThreatService service =
            new ThreatService(threats, indicators, events, new FixedClockPort(NOW), new SequentialIdGenerator());

    @Test
    void createOwnsTheThreatWithTheCallersTenantAndDefaults() {
        Threat created = service.create(command("AgentTesla", null), actor(OWNER));

        assertThat(created.ownerTenantId()).isEqualTo(OWNER);
        // §9.7 的規則:預設 AMBER(私有),不是 CLEAR
        assertThat(created.tlp()).isEqualTo(Tlp.AMBER);
        assertThat(created.status()).isEqualTo(ThreatStatus.ACTIVE);
        assertThat(created.snapshot().firstSeen()).isEqualTo(NOW);
        assertThat(created.snapshot().lastSeen()).isEqualTo(NOW);
    }

    @Test
    void h1DuplicateIdentityIsAConflict() {
        service.create(command("AgentTesla", null), actor(OWNER));

        assertThatThrownBy(() -> service.create(command("AgentTesla", null), actor(OWNER)))
                .isInstanceOf(ThreatConflictException.class)
                .hasMessageContaining("H1");
        // 別的租戶可以用同一個名字(識別鍵含 ownerTenantId)
        assertThat(service.create(command("AgentTesla", null), actor(STRANGER))).isNotNull();
    }

    @Test
    void h6LinkingAStricterIndicatorTightensTheThreatTlp() {
        Threat threat = service.create(command("AgentTesla", null), actor(OWNER)); // AMBER
        Indicator stricter = storedIndicator(OWNER, Tlp.AMBER_STRICT);

        Optional<Threat> linked = service.linkIndicator(threat.id(), stricter.id(), IndicatorRole.C2, actor(OWNER));

        assertThat(linked).isPresent();
        assertThat(linked.orElseThrow().tlp()).isEqualTo(Tlp.AMBER_STRICT);
    }

    /** 發布(CLEAR/GREEN)需要 ioc:publish,且擁有者轉為 public tenant(§9.7 的規則,ADR 0027 沿用)。 */
    @Test
    void publishingRequiresIocPublishAndMovesOwnershipToThePublicTenant() {
        assertThatThrownBy(() -> service.create(command("AgentTesla-Public", Tlp.CLEAR), actor(OWNER)))
                .isInstanceOf(PublishNotPermittedException.class);

        Threat published = service.create(command("AgentTesla-Public", Tlp.CLEAR), publisher(OWNER));

        assertThat(published.ownerTenantId()).isEqualTo(TenantId.PUBLIC);
        assertThat(published.tlp()).isEqualTo(Tlp.CLEAR);
        // 發布者(持 ioc:publish)才改得動公開威脅
        assertThat(service.changeStatus(published.id(), ThreatStatus.DORMANT, actor(OWNER)))
                .isEmpty();
        assertThat(service.changeStatus(published.id(), ThreatStatus.DORMANT, publisher(OWNER)))
                .isPresent();
    }

    @Test
    void redIsRejectedBecauseItNeverEntersThePlatform() {
        assertThatThrownBy(() -> service.create(command("AgentTesla-Red", Tlp.RED), publisher(OWNER)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void h6LinkingALooserIndicatorNeverWidensTheThreatTlp() {
        Threat threat = service.create(command("AgentTesla", Tlp.AMBER), actor(OWNER));
        Indicator clear = storedIndicator(OWNER, Tlp.CLEAR);

        Optional<Threat> linked = service.linkIndicator(threat.id(), clear.id(), IndicatorRole.C2, actor(OWNER));

        assertThat(linked.orElseThrow().tlp()).isEqualTo(Tlp.AMBER);
    }

    /** ADR 0020 的核心:IOC 的 TLP 在多來源合併時事後收緊,對應 Threat 必須跟著收緊。 */
    @Test
    void h6IsMaintainedAfterTheIndicatorTlpIsTightenedLater() {
        Threat threat = service.create(command("AgentTesla", Tlp.AMBER), actor(OWNER));
        Indicator indicator = storedIndicator(OWNER, Tlp.AMBER);
        service.linkIndicator(threat.id(), indicator.id(), IndicatorRole.C2, actor(OWNER));

        int tightened = service.retightenForIndicator(indicator.id(), Tlp.AMBER_STRICT);

        assertThat(tightened).isEqualTo(1);
        assertThat(threats.findById(threat.id()).orElseThrow().tlp()).isEqualTo(Tlp.AMBER_STRICT);
        // 再跑一次不會重複收緊(冪等)
        assertThat(service.retightenForIndicator(indicator.id(), Tlp.AMBER_STRICT))
                .isZero();
    }

    @Test
    void unlinkingDoesNotWidenTheTlpBack() {
        Threat threat = service.create(command("AgentTesla", Tlp.AMBER), actor(OWNER));
        Indicator stricter = storedIndicator(OWNER, Tlp.AMBER_STRICT);
        service.linkIndicator(threat.id(), stricter.id(), IndicatorRole.C2, actor(OWNER));

        Optional<Threat> unlinked = service.unlinkIndicator(threat.id(), stricter.id(), actor(OWNER));

        assertThat(unlinked.orElseThrow().tlp()).isEqualTo(Tlp.AMBER_STRICT);
        assertThat(unlinked.orElseThrow().indicators()).isEmpty();
    }

    @Test
    void unlinkingSomethingThatWasNeverLinkedIsNotFound() {
        Threat threat = service.create(command("AgentTesla", null), actor(OWNER));
        Indicator indicator = storedIndicator(OWNER, Tlp.AMBER);

        assertThat(service.unlinkIndicator(threat.id(), indicator.id(), actor(OWNER)))
                .isEmpty();
    }

    @Test
    void anotherTenantsThreatIsNeverVisibleToWrites() {
        Threat threat = service.create(command("AgentTesla", null), actor(OWNER));
        Indicator indicator = storedIndicator(OWNER, Tlp.AMBER);

        assertThat(service.linkIndicator(threat.id(), indicator.id(), IndicatorRole.C2, actor(STRANGER)))
                .isEmpty();
        assertThat(service.changeStatus(threat.id(), ThreatStatus.RETIRED, actor(STRANGER)))
                .isEmpty();
        assertThat(service.addExternalReference(
                        threat.id(), new ExternalReference("cve", "CVE-2026-1", null, null), actor(STRANGER)))
                .isEmpty();
    }

    @Test
    void linkingAnIndicatorTheCallerCannotSeeIsNotFound() {
        Threat threat = service.create(command("AgentTesla", null), actor(OWNER));
        Indicator strangersIoc = storedIndicator(STRANGER, Tlp.AMBER);

        assertThat(service.linkIndicator(threat.id(), strangersIoc.id(), IndicatorRole.C2, actor(OWNER)))
                .isEmpty();
    }

    @Test
    void externalReferenceConflictsAndStatusConflictsAreSurfaced() {
        Threat threat = service.create(command("AgentTesla", null), actor(OWNER));
        ExternalReference reference = new ExternalReference("mitre-attack", "S0331", null, null);
        service.addExternalReference(threat.id(), reference, actor(OWNER));

        assertThatThrownBy(() -> service.addExternalReference(threat.id(), reference, actor(OWNER)))
                .isInstanceOf(ThreatConflictException.class);
        service.changeStatus(threat.id(), ThreatStatus.RETIRED, actor(OWNER));
        assertThatThrownBy(() -> service.changeStatus(threat.id(), ThreatStatus.ACTIVE, actor(OWNER)))
                .isInstanceOf(ThreatConflictException.class);
    }

    @Test
    void missingThreatIsNotFound() {
        ThreatId unknown = new ThreatId(UUID.fromString("00000000-0000-0000-0000-0000000000ff"));

        assertThat(service.changeStatus(unknown, ThreatStatus.DORMANT, actor(OWNER)))
                .isEmpty();
    }

    @Test
    void indicatorTlpTighteningIsPublishedByTheIndicatorAggregate() {
        Indicator indicator =
                IndicatorTestBuilder.activeIndicator(OWNER, Tlp.CLEAR, RedistributionPolicy.PUBLIC_REDISTRIBUTABLE);
        indicator.pullEvents();
        indicator.mergeFrom(
                new com.ctip.domain.indicator.IndicatorSource(IndicatorTestBuilder.report(IndicatorTestBuilder.SOURCE_B)
                        .tlp(Tlp.AMBER)
                        .build()),
                new com.ctip.domain.source.Reputation(60));

        assertThat(indicator.pullEvents())
                .filteredOn(IndicatorTlpTightened.class::isInstance)
                .singleElement()
                .satisfies(event -> {
                    IndicatorTlpTightened tightened = (IndicatorTlpTightened) event;
                    assertThat(tightened.previousTlp()).isEqualTo(Tlp.CLEAR);
                    assertThat(tightened.currentTlp()).isEqualTo(Tlp.AMBER);
                });
    }

    private Indicator storedIndicator(TenantId owner, Tlp tlp) {
        Indicator indicator =
                IndicatorTestBuilder.activeIndicator(owner, tlp, RedistributionPolicy.PUBLIC_REDISTRIBUTABLE);
        return indicators.save(indicator);
    }

    private static CreateThreatCommand command(String name, Tlp tlp) {
        return new CreateThreatCommand(
                ThreatType.MALWARE_FAMILY,
                name,
                Set.of("Agent Tesla"),
                "Commodity infostealer.",
                Severity.HIGH,
                70,
                tlp,
                Set.of("infostealer"),
                null,
                null);
    }

    /** TENANT_ADMIN:有 threat:manage,沒有 ioc:publish。 */
    private static AuthenticatedIdentity actor(TenantId tenantId) {
        return AuthenticatedIdentity.ofUser(
                new UserId(UUID.fromString("00000000-0000-0000-0000-000000000011")),
                tenantId,
                RoleCode.TENANT_ADMIN,
                Set.of("threat:manage"));
    }

    /** SYSTEM_ADMIN:另有 ioc:publish,可把威脅發布到 public tenant。 */
    private static AuthenticatedIdentity publisher(TenantId tenantId) {
        return AuthenticatedIdentity.ofUser(
                new UserId(UUID.fromString("00000000-0000-0000-0000-000000000012")),
                tenantId,
                RoleCode.SYSTEM_ADMIN,
                Set.of("threat:manage", "ioc:publish"));
    }
}
