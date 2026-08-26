package com.ctip.interfaces.rest.dto.source;

import java.util.UUID;

/** 來源概況(GET /sources、GET /sources/{id})。 */
public record SourceDto(
        UUID id,
        String sourceType,
        String displayName,
        String homepage,
        String defaultTlp,
        String redistributionPolicy,
        int reputation,
        boolean enabled,
        boolean syncable,
        String status,
        long totalRecordsIngested) {}
