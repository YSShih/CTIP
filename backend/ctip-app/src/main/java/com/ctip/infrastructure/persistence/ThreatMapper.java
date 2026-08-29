package com.ctip.infrastructure.persistence;

import com.ctip.domain.indicator.IndicatorId;
import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.threat.ExternalReference;
import com.ctip.domain.threat.IndicatorRole;
import com.ctip.domain.threat.Threat;
import com.ctip.domain.threat.ThreatId;
import com.ctip.domain.threat.ThreatIndicatorLink;
import com.ctip.domain.threat.ThreatSnapshot;
import com.ctip.domain.threat.ThreatStatus;
import com.ctip.domain.threat.ThreatType;
import com.ctip.sdk.Confidence;
import com.ctip.sdk.Severity;
import com.ctip.sdk.Tlp;
import java.util.Comparator;
import java.util.Set;
import java.util.UUID;
import org.mapstruct.Mapper;

/** Threat 聚合 ↔ JPA entity graph(表 19–21)。 */
@Mapper(componentModel = "spring")
interface ThreatMapper {

    default Threat toDomain(ThreatEntity e) {
        return Threat.reconstitute(new ThreatSnapshot(
                new ThreatId(e.id),
                new TenantId(e.ownerTenantId),
                ThreatType.valueOf(e.type),
                e.name,
                Set.of(e.aliases),
                e.description,
                Severity.valueOf(e.severity),
                Confidence.of(e.confidence),
                Tlp.valueOf(e.tlp),
                ThreatStatus.valueOf(e.status),
                e.firstSeen,
                e.lastSeen,
                Set.of(e.tags),
                e.indicators.stream()
                        .map(this::toLink)
                        .sorted(Comparator.comparing(ThreatIndicatorLink::addedAt))
                        .toList(),
                e.externalReferences.stream().map(this::toExternalReference).toList()));
    }

    default ThreatIndicatorLink toLink(ThreatIndicatorEntity link) {
        return new ThreatIndicatorLink(
                new IndicatorId(link.indicatorId), IndicatorRole.valueOf(link.role), link.addedAt);
    }

    default ExternalReference toExternalReference(ThreatExternalReferenceEntity reference) {
        return new ExternalReference(reference.sourceName, reference.externalId, reference.url, reference.description);
    }

    default void updateEntity(ThreatSnapshot s, ThreatEntity e) {
        e.id = s.id().value();
        e.ownerTenantId = s.ownerTenantId().value();
        e.type = s.type().name();
        e.name = s.name();
        e.aliases = s.aliases().stream().sorted().toArray(String[]::new);
        e.description = s.description();
        e.severity = s.severity().name();
        e.confidence = (short) s.confidence().value();
        e.tlp = s.tlp().name();
        e.status = s.status().name();
        e.firstSeen = s.firstSeen();
        e.lastSeen = s.lastSeen();
        e.tags = s.tags().stream().sorted().toArray(String[]::new);
        reconcileIndicators(s, e);
        reconcileExternalReferences(s, e);
    }

    private void reconcileIndicators(ThreatSnapshot s, ThreatEntity e) {
        e.indicators.removeIf(existing -> s.indicators().stream()
                .noneMatch(link -> link.indicatorId().value().equals(existing.indicatorId)));
        for (ThreatIndicatorLink link : s.indicators()) {
            ThreatIndicatorEntity target = e.indicators.stream()
                    .filter(existing ->
                            existing.indicatorId.equals(link.indicatorId().value()))
                    .findFirst()
                    .orElseGet(() -> {
                        ThreatIndicatorEntity created = new ThreatIndicatorEntity();
                        created.threat = e;
                        created.indicatorId = link.indicatorId().value();
                        created.addedAt = link.addedAt();
                        e.indicators.add(created);
                        return created;
                    });
            target.role = link.role().name();
        }
    }

    private void reconcileExternalReferences(ThreatSnapshot s, ThreatEntity e) {
        e.externalReferences.removeIf(existing -> s.externalReferences().stream()
                .noneMatch(reference -> reference.identityKey().equals(identityKey(existing))));
        for (ExternalReference reference : s.externalReferences()) {
            ThreatExternalReferenceEntity target = e.externalReferences.stream()
                    .filter(existing -> identityKey(existing).equals(reference.identityKey()))
                    .findFirst()
                    .orElseGet(() -> {
                        ThreatExternalReferenceEntity created = new ThreatExternalReferenceEntity();
                        created.id = UUID.randomUUID();
                        created.threat = e;
                        created.sourceName = reference.sourceName();
                        created.externalId = reference.externalId();
                        e.externalReferences.add(created);
                        return created;
                    });
            target.url = reference.url();
            target.description = reference.description();
        }
    }

    /** 與 {@code ux_ter_identity_coalesced} 同一組鍵:null 的 external_id 視同空字串。 */
    private static String identityKey(ThreatExternalReferenceEntity reference) {
        return reference.sourceName + " " + (reference.externalId == null ? "" : reference.externalId);
    }
}
