package com.ctip.interfaces.rest.openapi;

import com.ctip.interfaces.rest.dto.common.ErrorResponse;
import com.ctip.interfaces.rest.dto.source.SourceDto;
import com.ctip.interfaces.rest.dto.source.SourceStatusDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;

/** 來源端點的 OpenAPI 文件(§9.6);controller 實作本介面以繼承註解。 */
@Tag(name = "Source", description = "Threat source catalog and health. Authentication: anonymous.")
public interface SourceApi {

    String SOURCE_EXAMPLE = "{\"id\":\"6f0d2c4e-93a5-4f6b-8c1d-2e3a4b5c6d7e\",\"sourceType\":\"MOCK_OPENPHISH\","
            + "\"displayName\":\"Mock OpenPhish\",\"homepage\":null,\"defaultTlp\":\"CLEAR\","
            + "\"redistributionPolicy\":\"ATTRIBUTION_REQUIRED\",\"reputation\":70,\"enabled\":true,"
            + "\"syncable\":true,\"status\":\"ACTIVE\",\"totalRecordsIngested\":56}";

    @Operation(
            summary = "List threat sources",
            description = "All configured threat sources with their TLP defaults, redistribution policy "
                    + "snapshot basis and health status. 認證:匿名。")
    @ApiResponse(
            responseCode = "200",
            description = "All sources",
            content =
                    @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = SourceDto.class)),
                            examples = @ExampleObject(value = "[" + SOURCE_EXAMPLE + "]")))
    @SecurityRequirements
    List<SourceDto> list();

    @Operation(summary = "Get one threat source", description = "A single source by id. 認證:匿名。")
    @ApiResponse(
            responseCode = "200",
            description = "The source",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SourceDto.class),
                            examples = @ExampleObject(value = SOURCE_EXAMPLE)))
    @ApiResponse(
            responseCode = "404",
            description = "NOT_FOUND",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @SecurityRequirements
    SourceDto byId(UUID id);

    @Operation(
            summary = "Get source health status",
            description = "Sync health for one source: status transitions (ACTIVE/DEGRADED/FAILED/DISABLED), "
                    + "consecutive failures, timestamps and masked last error. 認證:匿名。")
    @ApiResponse(
            responseCode = "200",
            description = "Health detail",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SourceStatusDto.class),
                            examples =
                                    @ExampleObject(
                                            value =
                                                    "{\"id\":\"6f0d2c4e-93a5-4f6b-8c1d-2e3a4b5c6d7e\","
                                                            + "\"status\":\"ACTIVE\",\"consecutiveFailures\":0,"
                                                            + "\"lastSyncAt\":\"2026-08-26T02:00:00Z\",\"lastSuccessAt\":\"2026-08-26T02:00:00Z\","
                                                            + "\"lastFailureAt\":null,\"lastErrorMessage\":null,\"avgLatencyMs\":12}")))
    @ApiResponse(
            responseCode = "404",
            description = "NOT_FOUND",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @SecurityRequirements
    SourceStatusDto status(UUID id);
}
