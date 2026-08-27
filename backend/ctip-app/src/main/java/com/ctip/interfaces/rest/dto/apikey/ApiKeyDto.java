package com.ctip.interfaces.rest.dto.apikey;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Set;

/** API key 的可查詢欄位。<strong>原文永不出現在此</strong>(不變量 K1)。 */
public record ApiKeyDto(
        @Schema(example = "9c7a1e42-5f3b-4a10-9d2c-7e8f0a1b2c3d")
        String id,

        @Schema(example = "ci-pipeline") String name,
        @Schema(example = "aB3xY9kQ") String keyPrefix,
        @ArraySchema(schema = @Schema(example = "ioc:read")) Set<String> scopes,
        @Schema(example = "2027-01-01T00:00:00Z") Instant expiresAt,
        @Schema(example = "2026-08-27T08:00:00Z") Instant lastUsedAt,
        @Schema(example = "null") Instant revokedAt,
        @Schema(example = "2026-08-27T07:00:00Z") Instant createdAt) {}
