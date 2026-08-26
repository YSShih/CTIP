package com.ctip.interfaces.rest.dto.stats;

import java.time.Instant;
import java.util.UUID;

/** 各來源筆數與健康(GET /stats/sources)。 */
public record SourceStatsDto(
        UUID sourceId,
        String sourceType,
        String displayName,
        String status,
        boolean enabled,
        long indicatorCount,
        Instant lastSuccessAt) {}
