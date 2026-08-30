package com.ctip.interfaces.rest.openapi;

import com.ctip.interfaces.rest.dto.audit.AuditLogDto;
import com.ctip.interfaces.rest.dto.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 稽核軌跡端點的 OpenAPI 文件(§9.1「通知與稽核」);controller 實作本介面以繼承註解。 */
@Tag(name = "Audit", description = "Append-only audit trail for the caller's tenant.")
public interface AuditLogApi {

    String PAGE_EXAMPLE = """
            {"items":[{"id":"0b6a5c2e-1f43-4c2b-9f0a-6d5e4c3b2a10",\
            "occurredAt":"2026-08-30T09:15:04Z","actorType":"USER",\
            "actorId":"a2f1c0d4-9b8e-4a71-8c33-0e1d2f3a4b5c","action":"IOC_SUBMIT",\
            "resourceType":"indicator","resourceId":null,"ip":"198.51.100.7",\
            "userAgent":"curl/8.7.1","result":"SUCCESS","traceId":"4bf92f3577b34da6a3ce929d0e0e4736",\
            "metadata":{"path":"/api/v1/iocs"}}],"nextCursor":null,"hasMore":false}""";

    @Operation(
            summary = "List audit log entries",
            description = "Cursor-paginated audit trail for the caller's own tenant, newest first. The trail is "
                    + "append-only: entries are never updated or deleted by the application, and are removed only "
                    + "by the retention job after AUDIT_RETENTION_DAYS. Optionally filtered by action. "
                    + "認證:需要 Bearer JWT 或 X-API-Key,權限 audit:read。")
    @ApiResponse(
            responseCode = "200",
            description = "One page of audit entries",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PageResponse.class),
                            examples = @ExampleObject(value = PAGE_EXAMPLE)))
    @ApiResponse(responseCode = "400", description = "Unknown action, or cursor cannot be parsed (INVALID_CURSOR)")
    @ApiResponse(responseCode = "403", description = "Missing audit:read permission (FORBIDDEN)")
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "apiKeyAuth")
    PageResponse<AuditLogDto> listAuditLogs(String cursor, Integer limit, String action);
}
