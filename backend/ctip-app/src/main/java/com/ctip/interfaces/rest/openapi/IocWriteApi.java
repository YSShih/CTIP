package com.ctip.interfaces.rest.openapi;

import com.ctip.interfaces.rest.dto.ioc.FalsePositiveRequest;
import com.ctip.interfaces.rest.dto.ioc.ImportJobDto;
import com.ctip.interfaces.rest.dto.ioc.IocDto;
import com.ctip.interfaces.rest.dto.ioc.IocSubmitRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.ResponseEntity;

/** IOC 寫入端點的 OpenAPI 文件(§9.1「IOC — 寫入」、§9.7);controller 實作本介面以繼承註解。 */
@Tag(name = "IOC Write", description = "Manual submission, bulk import and false-positive reporting.")
public interface IocWriteApi {

    String IOC_EXAMPLE = """
            {"id":"7c9e6679-7425-40de-944b-e07fc1f90ae7","type":"IPV4","value":"203.0.113.5",\
            "hashType":null,"confidence":80,"severity":"HIGH","score":72,"tlp":"AMBER","status":"ACTIVE",\
            "firstSeen":"2026-08-28T09:00:00Z","lastSeen":"2026-08-28T09:00:00Z","validUntil":null,\
            "tags":["internal-incident-2026-08"],"sourceCount":1,"attribution":[]}""";

    String JOB_EXAMPLE = """
            {"importJobId":"0f2d7b3c-9a41-4a7e-8b2f-1c5d6e7f8a90","status":"PENDING","format":"CSV",\
            "totalRows":1200,"acceptedCount":0,"mergedCount":0,"rejectedCount":0,"errorMessage":null,\
            "startedAt":null,"finishedAt":null,"createdAt":"2026-08-28T09:00:00Z"}""";

    @Operation(
            summary = "Submit a single IOC",
            description = "Runs the full ingestion pipeline (validation, normalisation, deduplication, merge) — "
                    + "no stage is bypassed. The owning tenant is always the submitter's; it cannot be chosen. "
                    + "TLP defaults to AMBER; CLEAR/GREEN additionally require ioc:publish and transfer "
                    + "ownership to the public tenant. Returns 201 for a new IOC, 200 when merged into an "
                    + "existing one. 認證:需要 Bearer JWT 或 X-API-Key,權限 ioc:submit。")
    @ApiResponse(
            responseCode = "201",
            description = "IOC created",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = IocDto.class),
                            examples = @ExampleObject(value = IOC_EXAMPLE)))
    @ApiResponse(responseCode = "200", description = "Merged into an existing IOC")
    @ApiResponse(responseCode = "400", description = "Rejected by the pipeline, e.g. private IP (INVALID_IOC_FORMAT)")
    @ApiResponse(
            responseCode = "403",
            description = "Missing ioc:submit / ioc:publish, or the plan disables "
                    + "manual submission (FORBIDDEN / PLAN_LIMIT_EXCEEDED)")
    @ApiResponse(responseCode = "429", description = "Daily submission quota exhausted (RATE_LIMIT_EXCEEDED)")
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "apiKeyAuth")
    ResponseEntity<IocDto> submitIoc(IocSubmitRequest request);

    @Operation(
            summary = "Bulk import IOCs",
            description = "Accepts text/csv or a STIX 2.1 bundle (application/json). Processed asynchronously: "
                    + "returns 202 with an importJobId, poll GET /iocs/import/{jobId} for progress. Imported "
                    + "IOCs are always private to the tenant (TLP:AMBER). Rows beyond the plan's daily quota "
                    + "are recorded individually as QUOTA_EXCEEDED rather than failing the whole file. "
                    + "認證:需要 Bearer JWT 或 X-API-Key,權限 ioc:import。")
    @ApiResponse(
            responseCode = "202",
            description = "Import accepted",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ImportJobDto.class),
                            examples = @ExampleObject(value = JOB_EXAMPLE)))
    @ApiResponse(responseCode = "400", description = "Undecodable payload (INVALID_REQUEST)")
    @ApiResponse(responseCode = "403", description = "Plan does not allow importing (PLAN_LIMIT_EXCEEDED)")
    @ApiResponse(responseCode = "413", description = "File exceeds the plan's row limit (PAYLOAD_TOO_LARGE)")
    @ApiResponse(responseCode = "415", description = "Unsupported Content-Type (UNSUPPORTED_MEDIA_TYPE)")
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "apiKeyAuth")
    ResponseEntity<ImportJobDto> importIocs(byte[] payload, String contentType);

    @Operation(
            summary = "Get import progress",
            description = "Jobs of another tenant are reported as not found rather than forbidden. "
                    + "認證:需要 Bearer JWT 或 X-API-Key,權限 ioc:import。")
    @ApiResponse(
            responseCode = "200",
            description = "Job status",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ImportJobDto.class),
                            examples = @ExampleObject(value = JOB_EXAMPLE)))
    @ApiResponse(responseCode = "404", description = "Unknown job or another tenant's job (NOT_FOUND)")
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "apiKeyAuth")
    ImportJobDto importStatus(UUID jobId);

    @Operation(
            summary = "Report a false positive",
            description = "Marks the MANUAL source record of the IOC as FALSE_POSITIVE and re-runs the merge "
                    + "policy — the resulting status is decided by that policy, never by the caller. Only the "
                    + "caller tenant's own IOCs are accepted; reports against public intelligence go through "
                    + "the platform appeal process. 認證:需要 Bearer JWT 或 X-API-Key,權限 ioc:report-fp。")
    @ApiResponse(
            responseCode = "200",
            description = "Report recorded",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = IocDto.class),
                            examples = @ExampleObject(value = IOC_EXAMPLE)))
    @ApiResponse(responseCode = "403", description = "The IOC belongs to the public intelligence pool (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Unknown or invisible IOC (NOT_FOUND)")
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "apiKeyAuth")
    IocDto reportFalsePositive(UUID id, FalsePositiveRequest request);
}
