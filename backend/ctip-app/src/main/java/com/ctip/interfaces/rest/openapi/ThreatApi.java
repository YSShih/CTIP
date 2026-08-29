package com.ctip.interfaces.rest.openapi;

import com.ctip.interfaces.rest.dto.common.ErrorResponse;
import com.ctip.interfaces.rest.dto.common.PageResponse;
import com.ctip.interfaces.rest.dto.threat.ThreatDto;
import com.ctip.interfaces.rest.dto.threat.ThreatIndicatorDto;
import com.ctip.interfaces.rest.dto.threat.ThreatListParams;
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

/** Threat 讀取端點的 OpenAPI 文件(§9.6);controller 實作本介面以繼承註解。 */
@Tag(
        name = "Threat",
        description = "Read access to threats (campaigns, malware families, actors) and their linked "
                + "indicators. Authentication: anonymous (public TLP:CLEAR only; cross-tenant data is "
                + "always 404).")
public interface ThreatApi {

    String THREAT_EXAMPLE = """
            {"id":"5b8f9d2e-1c3a-4f7b-9e0d-2a4c6b8d0f13","type":"MALWARE_FAMILY","name":"AgentTesla",\
            "aliases":["Agent Tesla"],"description":"Commodity infostealer.","severity":"HIGH",\
            "confidence":70,"tlp":"CLEAR","status":"ACTIVE","firstSeen":"2026-01-15T00:00:00Z",\
            "lastSeen":"2026-08-20T00:00:00Z","tags":["infostealer"],"indicatorCount":2,\
            "externalReferences":[{"sourceName":"mitre-attack","externalId":"S0331","url":null,\
            "description":null}]}""";

    String PAGE_EXAMPLE = "{\"items\":[" + THREAT_EXAMPLE
            + "],\"nextCursor\":\"eyJscyI6IjIwMjYtMDgtMjBUMDA6MDA6MDBaIiwiaWQiOiIuLi4ifQ==\",\"hasMore\":true}";

    String LINK_EXAMPLE = """
            [{"role":"C2","addedAt":"2026-08-20T10:00:00Z","ioc":{"id":"1f0d2c4e-93a5-4f6b-8c1d-2e3a4b5c6d7e",\
            "type":"DOMAIN","hashType":null,"value":"mal-8.ctip-sample.net","confidence":60,"severity":"HIGH",\
            "score":42,"tlp":"CLEAR","status":"ACTIVE","firstSeen":"2026-08-01T00:00:00Z",\
            "lastSeen":"2026-08-20T10:00:00Z","validUntil":null,"sourceCount":1,"tags":["phishing"],\
            "attribution":[]}}]""";

    @Operation(
            summary = "List threats (cursor pagination)",
            description = "Lists visible threats ordered by (lastSeen DESC, id DESC). Filters: type, status, "
                    + "severity, tlp, name (case-insensitive substring), tags and aliases (repeat the "
                    + "parameter; all must match). RETIRED threats are excluded unless includeRetired=true "
                    + "or status is given explicitly. limit above the plan maximum is clamped, not rejected. "
                    + "認證:匿名(僅 public TLP:CLEAR)。")
    @ApiResponse(
            responseCode = "200",
            description = "One page of threats",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PageResponse.class),
                            examples = @ExampleObject(value = PAGE_EXAMPLE)))
    @ApiResponse(
            responseCode = "400",
            description = "INVALID_CURSOR / INVALID_REQUEST",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @SecurityRequirements
    PageResponse<ThreatDto> list(ThreatListParams params);

    @Operation(
            summary = "Get one threat",
            description = "Returns a single threat by id. Cross-tenant or invisible resources are always 404 "
                    + "(existence is never disclosed). indicatorCount is the total number of links, not the "
                    + "number visible to the caller. 認證:匿名。")
    @ApiResponse(
            responseCode = "200",
            description = "The threat",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ThreatDto.class),
                            examples = @ExampleObject(value = THREAT_EXAMPLE)))
    @ApiResponse(
            responseCode = "404",
            description = "NOT_FOUND (missing or not visible)",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @SecurityRequirements
    ThreatDto byId(UUID id);

    @Operation(
            summary = "List the IOCs linked to a threat",
            description = "Returns the linked indicators ordered by addedAt. Each indicator is filtered by the "
                    + "same TLP and redistribution rules as GET /iocs — a link never exposes an IOC the "
                    + "caller could not read directly, so this list can be shorter than indicatorCount. "
                    + "認證:匿名。")
    @ApiResponse(
            responseCode = "200",
            description = "The visible linked indicators",
            content =
                    @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = ThreatIndicatorDto.class)),
                            examples = @ExampleObject(value = LINK_EXAMPLE)))
    @ApiResponse(
            responseCode = "404",
            description = "NOT_FOUND (missing or not visible)",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @SecurityRequirements
    List<ThreatIndicatorDto> indicators(UUID id);
}
