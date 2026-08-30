package com.ctip.interfaces.rest.openapi;

import com.ctip.interfaces.rest.dto.admin.SourceAdminDto;
import com.ctip.interfaces.rest.dto.admin.SourcePatchRequest;
import com.ctip.interfaces.rest.dto.admin.SourceSyncResultDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;

/** 來源管理端點的 OpenAPI 文件(§9.1「管理」);controller 實作本介面以繼承註解。 */
@Tag(name = "Admin", description = "Platform administration. Every call is audited as ADMIN_ACTION.")
public interface AdminSourceApi {

    String SYNC_EXAMPLE = """
            {"sourceId":"3f4a1c0e-2b7d-4f10-9c11-8a2e5d6b7c90","success":true,"recordsFetched":120,\
            "recordsAccepted":98,"recordsRejected":4,"recordsMerged":18,"failureReason":null}""";

    String SOURCE_EXAMPLE = """
            {"id":"3f4a1c0e-2b7d-4f10-9c11-8a2e5d6b7c90","enabled":false,"status":"DISABLED",\
            "lastErrorMessage":null}""";

    @Operation(
            summary = "Trigger a source synchronisation now",
            description = "Runs one synchronisation for the source immediately, ignoring its recommended "
                    + "interval. Disabled sources are rejected. Fetching happens outside the request's "
                    + "transaction and the response reports what the run ingested. "
                    + "認證:需要 Bearer JWT 或 X-API-Key,權限 source:sync。")
    @ApiResponse(
            responseCode = "200",
            description = "The run finished; success=false means the source failed and the reason is masked",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SourceSyncResultDto.class),
                            examples = @ExampleObject(value = SYNC_EXAMPLE)))
    @ApiResponse(responseCode = "403", description = "Missing source:sync permission (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "No such source (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "The source is disabled (CONFLICT)")
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "apiKeyAuth")
    SourceSyncResultDto syncNow(UUID id);

    @Operation(
            summary = "Enable or disable a source",
            description = "enabled is the only externally decided field on a source; health, cursor and counters "
                    + "are written by ingestion itself. Disabling stops both scheduled and manual synchronisation. "
                    + "認證:需要 Bearer JWT 或 X-API-Key,權限 source:manage。")
    @ApiResponse(
            responseCode = "200",
            description = "The updated source",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SourceAdminDto.class),
                            examples = @ExampleObject(value = SOURCE_EXAMPLE)))
    @ApiResponse(responseCode = "400", description = "enabled is missing (INVALID_REQUEST)")
    @ApiResponse(responseCode = "403", description = "Missing source:manage permission (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "No such source (NOT_FOUND)")
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "apiKeyAuth")
    SourceAdminDto updateSource(UUID id, SourcePatchRequest request);
}
