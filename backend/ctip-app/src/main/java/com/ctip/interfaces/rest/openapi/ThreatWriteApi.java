package com.ctip.interfaces.rest.openapi;

import com.ctip.interfaces.rest.dto.common.ErrorResponse;
import com.ctip.interfaces.rest.dto.threat.ExternalReferenceRequest;
import com.ctip.interfaces.rest.dto.threat.ThreatCreateRequest;
import com.ctip.interfaces.rest.dto.threat.ThreatDto;
import com.ctip.interfaces.rest.dto.threat.ThreatLinkRequest;
import com.ctip.interfaces.rest.dto.threat.ThreatStatusRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.ResponseEntity;

/**
 * Threat 寫入端點的 OpenAPI 文件(§9.1「Threat — 寫入」;ADR 0027 新增)。
 * controller 實作本介面以繼承註解。
 */
@Tag(
        name = "Threat Write",
        description = "Curation of threats: create, link/unlink IOCs, add external references, change status.")
public interface ThreatWriteApi {

    String THREAT_EXAMPLE = """
            {"id":"5b8f9d2e-1c3a-4f7b-9e0d-2a4c6b8d0f13","type":"MALWARE_FAMILY","name":"AgentTesla",\
            "aliases":["Agent Tesla"],"description":"Commodity infostealer.","severity":"HIGH",\
            "confidence":70,"tlp":"AMBER","status":"ACTIVE","firstSeen":"2026-01-15T00:00:00Z",\
            "lastSeen":"2026-08-20T00:00:00Z","tags":["infostealer"],"indicatorCount":1,\
            "externalReferences":[]}""";

    @Operation(
            summary = "Create a threat",
            description = "The owning tenant is always the caller's; it cannot be chosen. TLP defaults to "
                    + "AMBER (private); CLEAR/GREEN additionally require ioc:publish and transfer ownership "
                    + "to the public tenant (same rule as POST /iocs); RED is rejected. The TLP is tightened "
                    + "automatically when stricter IOCs are linked (invariant H6) and is never widened — "
                    + "linking a private IOC to a public threat therefore takes that threat out of public "
                    + "visibility. (ownerTenantId, type, name) must be unique within the tenant (H1). "
                    + "認證:需要 Bearer JWT 或 X-API-Key,權限 threat:manage。")
    @ApiResponse(
            responseCode = "201",
            description = "Threat created",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ThreatDto.class),
                            examples = @ExampleObject(value = THREAT_EXAMPLE)))
    @ApiResponse(
            responseCode = "409",
            description = "A threat with the same (type, name) already exists in this tenant (CONFLICT)",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "apiKeyAuth")
    ResponseEntity<ThreatDto> create(ThreatCreateRequest request);

    @Operation(
            summary = "Link an IOC to a threat (or change its role)",
            description = "Idempotent: linking an already linked IOC only updates its role. The link stores "
                    + "the indicator id only (invariant H5). Linking a stricter IOC tightens the threat's TLP "
                    + "(H6). Both the threat and the IOC must belong to — or be visible to — the caller's "
                    + "tenant; otherwise 404. 認證:需要 Bearer JWT 或 X-API-Key,權限 threat:manage。")
    @ApiResponse(
            responseCode = "200",
            description = "The updated threat",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ThreatDto.class),
                            examples = @ExampleObject(value = THREAT_EXAMPLE)))
    @ApiResponse(
            responseCode = "404",
            description = "Threat or IOC missing / not visible (NOT_FOUND)",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "apiKeyAuth")
    ThreatDto linkIndicator(UUID id, UUID indicatorId, ThreatLinkRequest request);

    @Operation(
            summary = "Unlink an IOC from a threat",
            description = "Removes the link and its STIX relationship projection. The threat's TLP is not "
                    + "widened — tightening is one-way. Returns 404 when the link does not exist. "
                    + "認證:需要 Bearer JWT 或 X-API-Key,權限 threat:manage。")
    @ApiResponse(
            responseCode = "200",
            description = "The updated threat",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ThreatDto.class),
                            examples = @ExampleObject(value = THREAT_EXAMPLE)))
    @ApiResponse(
            responseCode = "404",
            description = "Threat or link missing / not visible (NOT_FOUND)",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "apiKeyAuth")
    ThreatDto unlinkIndicator(UUID id, UUID indicatorId);

    @Operation(
            summary = "Add an external reference to a threat",
            description = "externalId or url must be present (invariant H3); (sourceName, externalId) is "
                    + "unique within the threat (H4, enforced with COALESCE so a null externalId still "
                    + "collides). 認證:需要 Bearer JWT 或 X-API-Key,權限 threat:manage。")
    @ApiResponse(
            responseCode = "201",
            description = "Reference added; returns the updated threat",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ThreatDto.class),
                            examples = @ExampleObject(value = THREAT_EXAMPLE)))
    @ApiResponse(
            responseCode = "400",
            description = "Neither externalId nor url given (INVALID_REQUEST)",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "409",
            description = "Duplicate (sourceName, externalId) within the threat (CONFLICT)",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "apiKeyAuth")
    ResponseEntity<ThreatDto> addExternalReference(UUID id, ExternalReferenceRequest request);

    @Operation(
            summary = "Change a threat's status",
            description = "ACTIVE / DORMANT / RETIRED. RETIRED is terminal: a retired threat accepts no "
                    + "further changes (create a new threat instead). Setting the status it already has "
                    + "returns 409 rather than silently succeeding. "
                    + "認證:需要 Bearer JWT 或 X-API-Key,權限 threat:manage。")
    @ApiResponse(
            responseCode = "200",
            description = "The updated threat",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ThreatDto.class),
                            examples = @ExampleObject(value = THREAT_EXAMPLE)))
    @ApiResponse(
            responseCode = "409",
            description = "Already retired, or already in that status (CONFLICT)",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "apiKeyAuth")
    ThreatDto changeStatus(UUID id, ThreatStatusRequest request);
}
