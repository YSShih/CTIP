package com.ctip.interfaces.rest.openapi;

import com.ctip.interfaces.rest.dto.admin.AssignPlanRequest;
import com.ctip.interfaces.rest.dto.admin.SubscriptionAssignmentDto;
import com.ctip.interfaces.rest.dto.admin.TenantOverviewDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;

/** 租戶管理端點的 OpenAPI 文件(§9.1「管理」);controller 實作本介面以繼承註解。 */
@Tag(name = "Admin", description = "Platform administration. Every call is audited as ADMIN_ACTION.")
public interface AdminTenantApi {

    String TENANTS_EXAMPLE = """
            [{"id":"00000000-0000-0000-0000-000000000001","slug":"public","name":"Public",\
            "type":"SYSTEM","status":"ACTIVE","planCode":"FREE"}]""";

    String SUBSCRIPTION_EXAMPLE = """
            {"subscriptionId":"7c1f0a35-2b6d-4c11-9e83-5f0a1b2c3d4e",\
            "tenantId":"a2f1c0d4-9b8e-4a71-8c33-0e1d2f3a4b5c","planCode":"PREMIUM",\
            "status":"ACTIVE","cancelledAt":null}""";

    @Operation(
            summary = "List all tenants",
            description = "Every tenant on the platform with the plan it currently holds; tenants without an "
                    + "active subscription report FREE. 認證:需要 Bearer JWT 或 X-API-Key,權限 system:admin。")
    @ApiResponse(
            responseCode = "200",
            description = "All tenants, ordered by slug",
            content =
                    @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = TenantOverviewDto.class)),
                            examples = @ExampleObject(value = TENANTS_EXAMPLE)))
    @ApiResponse(responseCode = "403", description = "Missing system:admin permission (FORBIDDEN)")
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "apiKeyAuth")
    List<TenantOverviewDto> listTenants();

    @Operation(
            summary = "Assign or cancel a tenant's plan",
            description = "Assigns the given plan to the tenant, creating a MANUAL subscription when none is "
                    + "active; planCode=CANCEL cancels the active subscription instead. A cancelled subscription "
                    + "can never return to ACTIVE (invariant B3) — assigning a plan afterwards creates a new one. "
                    + "認證:需要 Bearer JWT 或 X-API-Key,權限 system:admin。")
    @ApiResponse(
            responseCode = "200",
            description = "The resulting subscription",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SubscriptionAssignmentDto.class),
                            examples = @ExampleObject(value = SUBSCRIPTION_EXAMPLE)))
    @ApiResponse(responseCode = "400", description = "Unknown plan code (INVALID_REQUEST)")
    @ApiResponse(responseCode = "403", description = "Missing system:admin permission (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "No such plan, or the tenant has no subscription to cancel")
    @ApiResponse(responseCode = "409", description = "The public tenant cannot hold a subscription (CONFLICT)")
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "apiKeyAuth")
    SubscriptionAssignmentDto assignPlan(UUID id, AssignPlanRequest request);
}
