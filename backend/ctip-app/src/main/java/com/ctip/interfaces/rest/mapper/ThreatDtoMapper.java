package com.ctip.interfaces.rest.mapper;

import com.ctip.domain.threat.ExternalReference;
import com.ctip.domain.threat.Threat;
import com.ctip.domain.threat.ThreatSnapshot;
import com.ctip.interfaces.rest.dto.threat.ExternalReferenceDto;
import com.ctip.interfaces.rest.dto.threat.ThreatDto;
import org.mapstruct.Mapper;

/** Threat domain → 回應 DTO(docs/spec/09-api.md §9.5)。 */
@Mapper(componentModel = "spring")
public interface ThreatDtoMapper {

    default ThreatDto toDto(Threat threat) {
        ThreatSnapshot s = threat.snapshot();
        return new ThreatDto(
                s.id().value(),
                s.type().name(),
                s.name(),
                s.aliases(),
                s.description(),
                s.severity().name(),
                s.confidence().value(),
                s.tlp().name(),
                s.status().name(),
                s.firstSeen(),
                s.lastSeen(),
                s.tags(),
                s.indicators().size(),
                s.externalReferences().stream().map(this::toDto).toList());
    }

    default ExternalReferenceDto toDto(ExternalReference reference) {
        return new ExternalReferenceDto(
                reference.sourceName(), reference.externalId(), reference.url(), reference.description());
    }
}
