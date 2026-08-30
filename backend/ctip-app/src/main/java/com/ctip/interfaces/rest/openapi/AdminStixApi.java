package com.ctip.interfaces.rest.openapi;

import com.ctip.interfaces.rest.dto.admin.StixRebuildResultDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/** STIX 重建端點的 OpenAPI 文件(§9.1「管理」);controller 實作本介面以繼承註解。 */
@Tag(name = "Admin", description = "Platform administration. Every call is audited as ADMIN_ACTION.")
public interface AdminStixApi {

    @Operation(
            summary = "Rebuild every STIX projection from the domain",
            description = "stix_objects is derived data and can always be recomputed from indicators. Use this "
                    + "after projection writes have failed or after the projection rules changed. Runs in "
                    + "batches; a single bad row is logged and skipped rather than aborting the rebuild. "
                    + "認證:需要 Bearer JWT 或 X-API-Key,權限 system:admin。")
    @ApiResponse(
            responseCode = "200",
            description = "How many indicators were re-projected",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = StixRebuildResultDto.class),
                            examples = @ExampleObject(value = "{\"indicatorsRebuilt\":1020}")))
    @ApiResponse(responseCode = "403", description = "Missing system:admin permission (FORBIDDEN)")
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "apiKeyAuth")
    StixRebuildResultDto rebuildStix();
}
