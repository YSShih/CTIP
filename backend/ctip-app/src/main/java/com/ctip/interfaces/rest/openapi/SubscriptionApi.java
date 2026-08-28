package com.ctip.interfaces.rest.openapi;

import com.ctip.interfaces.rest.dto.subscription.SubscriptionDto;
import com.ctip.interfaces.rest.dto.subscription.SubscriptionUsageDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 訂閱端點的 OpenAPI 文件(§9.1「訂閱與 API Key」);controller 實作本介面以繼承註解。 */
@Tag(name = "Subscription", description = "The caller tenant's plan and quota usage.")
public interface SubscriptionApi {

    String SUBSCRIPTION_EXAMPLE = """
            {"planCode":"PREMIUM","planName":"Premium","tier":2,"status":"ACTIVE","provider":"MANUAL",\
            "currentPeriodStart":"2026-08-01T00:00:00Z","currentPeriodEnd":"2027-08-01T00:00:00Z",\
            "cancelledAt":null,"quotas":{"requestsPerMinute":1200,"requestsPerDay":500000,\
            "maxPageSize":500,"maxBatchLookup":1000,"minSyncIntervalSeconds":300,"publicBloomEnabled":true,\
            "tenantBloomCapacity":1000000,"websocketEnabled":true,"maxWebhooks":5,"maxApiKeys":10,\
            "customFeedEnabled":false,"stixExportMaxObjects":50000,"maxManualSubmissionsPerDay":1000,\
            "maxImportRowsPerFile":10000}}""";

    String USAGE_EXAMPLE = """
            {"planCode":"PREMIUM","manualSubmissionsToday":{"used":12,"limit":1000,\
            "resetAt":"2026-08-29T00:00:00Z"},"apiKeys":{"used":2,"limit":10,"resetAt":null}}""";

    @Operation(
            summary = "Get the current plan",
            description = "Returns the plan in effect for the caller's tenant together with all quota values. "
                    + "A tenant without an active subscription is on FREE (invariant B4). "
                    + "認證:需要 Bearer JWT 或 X-API-Key,權限 subscription:read。")
    @ApiResponse(
            responseCode = "200",
            description = "Plan in effect",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SubscriptionDto.class),
                            examples = @ExampleObject(value = SUBSCRIPTION_EXAMPLE)))
    @ApiResponse(responseCode = "403", description = "Missing subscription:read permission (FORBIDDEN)")
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "apiKeyAuth")
    SubscriptionDto subscription();

    @Operation(
            summary = "Get quota usage",
            description = "Current consumption against the plan's quotas. A null limit means unlimited; "
                    + "0 means the capability is disabled on this plan. "
                    + "認證:需要 Bearer JWT 或 X-API-Key,權限 subscription:read。")
    @ApiResponse(
            responseCode = "200",
            description = "Usage against the plan's quotas",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SubscriptionUsageDto.class),
                            examples = @ExampleObject(value = USAGE_EXAMPLE)))
    @ApiResponse(responseCode = "403", description = "Missing subscription:read permission (FORBIDDEN)")
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "apiKeyAuth")
    SubscriptionUsageDto subscriptionUsage();
}
