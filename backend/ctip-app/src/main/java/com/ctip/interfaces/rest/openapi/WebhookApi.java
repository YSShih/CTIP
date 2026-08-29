package com.ctip.interfaces.rest.openapi;

import com.ctip.interfaces.rest.dto.notification.IssuedWebhookDto;
import com.ctip.interfaces.rest.dto.notification.WebhookCreateRequest;
import com.ctip.interfaces.rest.dto.notification.WebhookDto;
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
import org.springframework.http.ResponseEntity;

/** Webhook 管理端點的 OpenAPI 文件(§9.1「通知與稽核」);controller 實作本介面以繼承註解。 */
@Tag(name = "Webhook", description = "Outbound webhook subscriptions for the caller's tenant.")
public interface WebhookApi {

    String WEBHOOK_EXAMPLE = """
            {"id":"9d2b7d3e-1a44-4f0b-9a2f-0c1d2e3f4a5b","name":"SOC pipeline",\
            "targetUrl":"https://soc.example.com/hooks/ctip","eventTypes":["NEW_IOC","IOC_REVOKED"],\
            "filter":{"iocTypes":["IPV4"],"minSeverity":"HIGH","tags":[],"sourceIds":[]},\
            "status":"ACTIVE","consecutiveFailures":0,"lastDeliveryAt":null,"lastSuccessAt":null,\
            "createdAt":"2026-08-29T09:00:00Z"}""";

    String ISSUED_EXAMPLE = "{\"secret\":\"3Qp7…只此一次…\",\"webhook\":" + WEBHOOK_EXAMPLE + "}";

    @Operation(
            summary = "List webhooks",
            description = "All webhooks owned by the caller's tenant. The signing secret is never returned here; "
                    + "it is disclosed once, at creation (invariant W2). "
                    + "認證:需要 Bearer JWT 或 X-API-Key,權限 webhook:manage。")
    @ApiResponse(
            responseCode = "200",
            description = "The tenant's webhooks",
            content =
                    @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = WebhookDto.class)),
                            examples = @ExampleObject(value = "[" + WEBHOOK_EXAMPLE + "]")))
    @ApiResponse(responseCode = "403", description = "Missing webhook:manage permission (FORBIDDEN)")
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "apiKeyAuth")
    List<WebhookDto> listWebhooks();

    @Operation(
            summary = "Register a webhook",
            description = "Creates a webhook and returns the HMAC signing secret exactly once (invariant W2). "
                    + "targetUrl must be https (W1). Deliveries are signed as "
                    + "HMAC-SHA256(secret, timestamp + \\\".\\\" + body) and carry the five X-CTIP-* headers. "
                    + "The number of webhooks per tenant is capped by plans.max_webhooks (W6). "
                    + "認證:需要 Bearer JWT 或 X-API-Key,權限 webhook:manage。")
    @ApiResponse(
            responseCode = "201",
            description = "Created; secret returned once",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = IssuedWebhookDto.class),
                            examples = @ExampleObject(value = ISSUED_EXAMPLE)))
    @ApiResponse(responseCode = "400", description = "Invalid target URL or event type (INVALID_REQUEST)")
    @ApiResponse(responseCode = "403", description = "Plan webhook quota exhausted (PLAN_LIMIT_EXCEEDED)")
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "apiKeyAuth")
    ResponseEntity<IssuedWebhookDto> createWebhook(WebhookCreateRequest request);

    @Operation(
            summary = "Delete a webhook",
            description = "Removes a webhook owned by the caller's tenant, together with its delivery history. "
                    + "A webhook belonging to another tenant is reported as 404, not 403. "
                    + "認證:需要 Bearer JWT 或 X-API-Key,權限 webhook:manage。")
    @ApiResponse(
            responseCode = "204",
            description = "Deleted",
            content =
                    @Content(mediaType = "application/json", examples = @ExampleObject(name = "empty", value = "null")))
    @ApiResponse(responseCode = "404", description = "No such webhook in the caller's tenant (NOT_FOUND)")
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "apiKeyAuth")
    ResponseEntity<Void> deleteWebhook(UUID id);
}
