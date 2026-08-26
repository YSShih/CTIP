package com.ctip.interfaces.rest.openapi;

import com.ctip.interfaces.rest.dto.stats.SourceStatsDto;
import com.ctip.interfaces.rest.dto.stats.StatsSummaryDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;

/** Dashboard 統計端點的 OpenAPI 文件(§9.6);controller 實作本介面以繼承註解。 */
@Tag(name = "Stats", description = "Public dashboard statistics. Authentication: anonymous.")
public interface StatsApi {

    @Operation(
            summary = "Public statistics summary",
            description = "Total visible ACTIVE indicators, distribution by type and the last-7-day trend "
                    + "(daily counts by lastSeen, zero-filled). Scoped by the caller's visibility — "
                    + "anonymous callers see public TLP:CLEAR data only. 認證:匿名。")
    @ApiResponse(
            responseCode = "200",
            description = "Summary statistics",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = StatsSummaryDto.class),
                            examples =
                                    @ExampleObject(
                                            value =
                                                    "{\"totalActive\":231,\"byType\":{\"DOMAIN\":40,\"URL\":38},"
                                                            + "\"trend\":[{\"date\":\"2026-08-20\",\"count\":3},{\"date\":\"2026-08-21\",\"count\":0}]}")))
    @SecurityRequirements
    StatsSummaryDto summary();

    @Operation(
            summary = "Per-source counts and health",
            description =
                    "Indicator counts, enablement, health status and last success time per threat " + "source. 認證:匿名。")
    @ApiResponse(
            responseCode = "200",
            description = "One row per configured source",
            content =
                    @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = SourceStatsDto.class)),
                            examples =
                                    @ExampleObject(
                                            value = "[{\"sourceId\":\"6f0d2c4e-93a5-4f6b-8c1d-2e3a4b5c6d7e\","
                                                    + "\"sourceType\":\"MOCK_OPENPHISH\",\"displayName\":\"Mock OpenPhish\","
                                                    + "\"status\":\"ACTIVE\",\"enabled\":true,\"indicatorCount\":351,"
                                                    + "\"lastSuccessAt\":\"2026-08-26T02:00:00Z\"}]")))
    @SecurityRequirements
    List<SourceStatsDto> sources();
}
