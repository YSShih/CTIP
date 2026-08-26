package com.ctip.interfaces.rest.dto.ioc;

import java.time.Instant;
import java.util.UUID;

/** 來源明細(GET /iocs/{id}/sources):跨租戶時僅含可揭露政策的來源(07 §7.9 規則 5)。 */
public record IocSourceDto(
        UUID sourceId,
        String sourceName,
        Integer sourceConfidence,
        String sourceSeverity,
        String sourceTlp,
        Instant sourceFirstSeen,
        Instant sourceLastSeen,
        Instant sourceValidUntil,
        int reportCount,
        String status,
        String redistributionPolicy) {}
