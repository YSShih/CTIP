package com.ctip.interfaces.rest.dto.apikey;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Set;

/** 建立 API key。scopes 不得超出建立者在該租戶的權限(不變量 K4)。 */
public record ApiKeyCreateRequest(
        @NotBlank @Size(max = 128) @Schema(example = "ci-pipeline")
        String name,

        @NotNull @ArraySchema(schema = @Schema(example = "ioc:read"))
        Set<String> scopes,

        @Schema(example = "2027-01-01T00:00:00Z") Instant expiresAt) {}
