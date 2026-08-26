package com.ctip.interfaces.rest.dto.ioc;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * IOC 回應(docs/spec/09-api.md §9.5)。value 為 canonical(正規化後)值;
 * attribution 依再散布政策規則 4(ATTRIBUTION_REQUIRED 的來源必附標註)。
 */
public record IocDto(
        UUID id,
        String type,
        String hashType,
        String value,
        int confidence,
        String severity,
        int score,
        String tlp,
        String status,
        Instant firstSeen,
        Instant lastSeen,
        Instant validUntil,
        int sourceCount,
        Set<String> tags,
        List<AttributionDto> attribution) {}
