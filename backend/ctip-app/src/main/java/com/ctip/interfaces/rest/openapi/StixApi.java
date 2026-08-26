package com.ctip.interfaces.rest.openapi;

import com.ctip.interfaces.rest.dto.common.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

/** STIX 端點的 OpenAPI 文件(§9.6);controller 實作本介面以繼承註解。 */
@Tag(name = "STIX", description = "STIX 2.1 objects and bundle export (07 §7.8).")
public interface StixApi {

    @Operation(
            summary = "Export a STIX 2.1 bundle",
            description = "Exports visible, redistributable indicators plus the referenced TLP 2.0 "
                    + "marking-definitions as one STIX 2.1 bundle. Object count is capped by plan "
                    + "(PLAN_LIMIT_EXCEEDED above the cap). 認證:stix:export——匿名不可用;"
                    + "M1 尚無登入流程,一律回 403,Phase 13 起依 RBAC 判定。")
    @ApiResponse(
            responseCode = "200",
            description = "STIX 2.1 bundle",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples =
                                    @ExampleObject(
                                            value =
                                                    "{\"type\":\"bundle\",\"id\":\"bundle--7c94...\","
                                                            + "\"objects\":[{\"type\":\"marking-definition\"},{\"type\":\"indicator\"}]}")))
    @ApiResponse(
            responseCode = "403",
            description = "FORBIDDEN (anonymous) / PLAN_LIMIT_EXCEEDED (over object cap)",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @SecurityRequirements
    ResponseEntity<String> bundle();

    @Operation(
            summary = "Get one STIX object",
            description = "Returns a stored STIX 2.1 object by its STIX id: indicator projections "
                    + "(indicator--{uuid}) after visibility and redistribution filtering, and the five "
                    + "fixed TLP 2.0 marking-definitions served from constants. 認證:匿名。")
    @ApiResponse(
            responseCode = "200",
            description = "The STIX object (verbatim stored JSON for indicators)",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples =
                                    @ExampleObject(
                                            value =
                                                    "{\"type\":\"marking-definition\",\"spec_version\":\"2.1\","
                                                            + "\"id\":\"marking-definition--94868c89-83c2-464b-929b-a1a8aa3c8487\","
                                                            + "\"created\":\"2022-10-01T00:00:00.000Z\",\"name\":\"TLP:CLEAR\"}")))
    @ApiResponse(
            responseCode = "404",
            description = "NOT_FOUND (unknown id, invisible or non-redistributable)",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @SecurityRequirements
    ResponseEntity<Object> byStixId(
            @Parameter(description = "STIX id, e.g. indicator--{uuid} or marking-definition--{uuid}") String stixId);
}
