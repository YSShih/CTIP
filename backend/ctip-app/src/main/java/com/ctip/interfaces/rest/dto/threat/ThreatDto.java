package com.ctip.interfaces.rest.dto.threat;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Threat 回應(docs/spec/09-api.md §9.5 的 DTO 慣例)。
 * {@code indicatorCount} 是<strong>關聯總數</strong>,不是 viewer 看得到的數量——
 * 後者要看 {@code GET /threats/{id}/indicators}(關聯的 IOC 各自再過一次可見度)。
 */
public record ThreatDto(
        UUID id,
        String type,
        String name,
        Set<String> aliases,
        String description,
        String severity,
        int confidence,
        String tlp,
        String status,
        Instant firstSeen,
        Instant lastSeen,
        Set<String> tags,
        int indicatorCount,
        List<ExternalReferenceDto> externalReferences) {}
