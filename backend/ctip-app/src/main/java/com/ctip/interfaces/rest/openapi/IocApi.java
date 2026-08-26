package com.ctip.interfaces.rest.openapi;

import com.ctip.interfaces.rest.dto.common.ErrorResponse;
import com.ctip.interfaces.rest.dto.common.PageResponse;
import com.ctip.interfaces.rest.dto.ioc.IocDto;
import com.ctip.interfaces.rest.dto.ioc.IocListParams;
import com.ctip.interfaces.rest.dto.ioc.IocSourceDto;
import com.ctip.interfaces.rest.dto.ioc.LookupRequest;
import com.ctip.interfaces.rest.dto.ioc.LookupResponse;
import com.ctip.interfaces.rest.dto.ioc.SearchRequest;
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

/** IOC 讀取端點的 OpenAPI 文件(§9.6);controller 實作本介面以繼承註解。 */
@Tag(
        name = "IOC",
        description = "Read access to indicators of compromise. Authentication: anonymous "
                + "(public TLP:CLEAR only; cross-tenant data is always 404).")
public interface IocApi {

    String IOC_EXAMPLE = "{\"id\":\"1f0d2c4e-93a5-4f6b-8c1d-2e3a4b5c6d7e\",\"type\":\"DOMAIN\","
            + "\"hashType\":null,\"value\":\"mal-8.ctip-sample.net\",\"confidence\":60,\"severity\":\"HIGH\","
            + "\"score\":42,\"tlp\":\"CLEAR\",\"status\":\"ACTIVE\",\"firstSeen\":\"2026-08-01T00:00:00Z\","
            + "\"lastSeen\":\"2026-08-20T10:00:00Z\",\"validUntil\":\"2026-11-18T10:00:00Z\",\"sourceCount\":1,"
            + "\"tags\":[\"sample\",\"phishing\"],"
            + "\"attribution\":[{\"sourceName\":\"Mock OpenPhish\",\"homepage\":null}]}";
    String PAGE_EXAMPLE = "{\"items\":[" + IOC_EXAMPLE
            + "],\"nextCursor\":\"eyJscyI6IjIwMjYtMDgtMjBUMTA6MDA6MDBaIiwiaWQiOiIuLi4ifQ==\"," + "\"hasMore\":true}";

    @Operation(
            summary = "List IOCs (cursor pagination)",
            description = "Lists visible indicators ordered by (lastSeen DESC, id DESC). Filters: type, "
                    + "severity, status, tlp, tags (repeat the parameter; all must match), sourceId, "
                    + "confidenceMin/Max, scoreMin/Max, lastSeenFrom/To (ISO-8601); EXPIRED entries are "
                    + "excluded unless includeExpired=true. limit above the plan maximum is clamped, not "
                    + "rejected. offset mode is for page-number UIs only and is capped at 10000. "
                    + "認證:匿名(僅 public TLP:CLEAR)。")
    @ApiResponse(
            responseCode = "200",
            description = "One page of indicators",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PageResponse.class),
                            examples = @ExampleObject(value = PAGE_EXAMPLE)))
    @ApiResponse(
            responseCode = "400",
            description = "INVALID_CURSOR / OFFSET_TOO_LARGE / INVALID_REQUEST",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @SecurityRequirements
    PageResponse<IocDto> list(IocListParams params);

    @Operation(
            summary = "Get one IOC",
            description = "Returns a single indicator by id. Cross-tenant or invisible resources are "
                    + "always 404 (existence is never disclosed). 認證:匿名。")
    @ApiResponse(
            responseCode = "200",
            description = "The indicator",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = IocDto.class),
                            examples = @ExampleObject(value = IOC_EXAMPLE)))
    @ApiResponse(
            responseCode = "404",
            description = "NOT_FOUND (missing or not visible)",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @SecurityRequirements
    IocDto byId(UUID id);

    @Operation(
            summary = "Get IOC source records",
            description = "Per-source observations for one indicator. Redistribution policy is enforced: "
                    + "for non-owner viewers only PUBLIC_REDISTRIBUTABLE and ATTRIBUTION_REQUIRED records "
                    + "are returned (DERIVED_ONLY and INTERNAL_ONLY are masked). 認證:匿名。")
    @ApiResponse(
            responseCode = "200",
            description = "Visible source records (may be empty when policy masks them)",
            content =
                    @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = IocSourceDto.class)),
                            examples =
                                    @ExampleObject(
                                            value = "[{\"sourceId\":\"6f0d2c4e-93a5-4f6b-8c1d-2e3a4b5c6d7e\","
                                                    + "\"sourceName\":\"Mock OpenPhish\",\"sourceConfidence\":60,\"sourceSeverity\":\"HIGH\","
                                                    + "\"sourceTlp\":\"CLEAR\",\"sourceFirstSeen\":\"2026-08-01T00:00:00Z\","
                                                    + "\"sourceLastSeen\":\"2026-08-20T10:00:00Z\",\"sourceValidUntil\":null,"
                                                    + "\"reportCount\":2,\"status\":\"ACTIVE\","
                                                    + "\"redistributionPolicy\":\"ATTRIBUTION_REQUIRED\"}]")))
    @ApiResponse(
            responseCode = "404",
            description = "NOT_FOUND (missing or not visible)",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @SecurityRequirements
    List<IocSourceDto> sources(UUID id);

    @Operation(
            summary = "Search IOCs",
            description = "Substring search over the canonical (normalized) value with the same filters "
                    + "and cursor pagination as the list endpoint (type, severity, status, tlp, tags, "
                    + "sourceId, confidence/score ranges, lastSeen range). M1 backs this with PostgreSQL "
                    + "(pg_trgm / GIN indexes); M2 swaps in Elasticsearch behind the same contract. 認證:匿名。",
            requestBody =
                    @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            content =
                                    @Content(
                                            examples =
                                                    @ExampleObject(
                                                            value = "{\"query\":\"ctip-sample\",\"type\":\"DOMAIN\","
                                                                    + "\"tags\":[\"phishing\"],\"scoreMin\":20,"
                                                                    + "\"lastSeenFrom\":\"2026-08-01T00:00:00Z\","
                                                                    + "\"limit\":5}"))))
    @ApiResponse(
            responseCode = "200",
            description = "One page of matches",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PageResponse.class),
                            examples = @ExampleObject(value = PAGE_EXAMPLE)))
    @ApiResponse(
            responseCode = "400",
            description = "INVALID_REQUEST (blank query, unknown enum value, bad cursor)",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "415",
            description = "UNSUPPORTED_MEDIA_TYPE",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @SecurityRequirements
    PageResponse<IocDto> search(SearchRequest request);

    @Operation(
            summary = "Batch exact lookup",
            description = "Verifies up to the plan limit of values in one call (anonymous: 20). Values are "
                    + "cleaned and normalized before identity matching; unparsable or invisible values are "
                    + "reported as found=false, never as errors. Bloom-filter clients must confirm hits "
                    + "here (11 §11.6). 認證:匿名。",
            requestBody =
                    @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            content =
                                    @Content(
                                            examples =
                                                    @ExampleObject(
                                                            value =
                                                                    "{\"values\":[\"MAL-8.CTIP-SAMPLE.NET.\",\"203.0.113.7\"]}"))))
    @ApiResponse(
            responseCode = "200",
            description = "Per-value verification results",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = LookupResponse.class),
                            examples =
                                    @ExampleObject(
                                            value = "{\"results\":[{\"value\":\"MAL-8.CTIP-SAMPLE.NET.\","
                                                    + "\"found\":true,\"ioc\":" + IOC_EXAMPLE + "},"
                                                    + "{\"value\":\"203.0.113.7\",\"found\":false,\"ioc\":null}]}")))
    @ApiResponse(
            responseCode = "413",
            description = "PAYLOAD_TOO_LARGE (batch above plan limit)",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @SecurityRequirements
    LookupResponse lookup(LookupRequest request);
}
