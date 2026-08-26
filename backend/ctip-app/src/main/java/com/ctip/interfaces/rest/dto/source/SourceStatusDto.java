package com.ctip.interfaces.rest.dto.source;

import java.time.Instant;
import java.util.UUID;

/** 來源健康狀態(GET /sources/{id}/status;lastErrorMessage 已經 CredentialMasker 遮罩)。 */
public record SourceStatusDto(
        UUID id,
        String status,
        int consecutiveFailures,
        Instant lastSyncAt,
        Instant lastSuccessAt,
        Instant lastFailureAt,
        String lastErrorMessage,
        Integer avgLatencyMs) {}
