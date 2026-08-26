package com.ctip.interfaces.rest.openapi;

import com.ctip.interfaces.rest.dto.system.HealthDto;
import com.ctip.interfaces.rest.dto.system.VersionDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 系統端點的 OpenAPI 文件(§9.6);controller 實作本介面以繼承註解,維持 controller 精簡。 */
@Tag(name = "System", description = "Liveness and version. Authentication: anonymous.")
public interface SystemApi {

    @Operation(
            summary = "Liveness probe",
            description = "Returns UP when the API is able to serve requests. Dependency health "
                    + "(database, etc.) is exposed separately at /actuator/health. 認證:匿名。")
    @ApiResponse(
            responseCode = "200",
            description = "Service is up",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = HealthDto.class),
                            examples = @ExampleObject(value = "{\"status\":\"UP\"}")))
    @SecurityRequirements
    HealthDto health();

    @Operation(
            summary = "API and build version",
            description = "Returns the API contract version (v1) and the build version of the running "
                    + "application (dev when running from sources). 認證:匿名。")
    @ApiResponse(
            responseCode = "200",
            description = "Version information",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = VersionDto.class),
                            examples = @ExampleObject(value = "{\"apiVersion\":\"v1\",\"version\":\"dev\"}")))
    @SecurityRequirements
    VersionDto version();
}
