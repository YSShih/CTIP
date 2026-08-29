package com.ctip.interfaces.rest.openapi;

import com.ctip.interfaces.rest.dto.common.PageResponse;
import com.ctip.interfaces.rest.dto.notification.NotificationDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.ResponseEntity;

/** 站內通知端點的 OpenAPI 文件(§9.1「通知與稽核」);controller 實作本介面以繼承註解。 */
@Tag(name = "Notification", description = "In-app notifications for the caller's tenant.")
public interface NotificationApi {

    String PAGE_EXAMPLE = """
            {"items":[{"id":"6f1d2f52-6f0a-4a6f-9a0f-2f1b6d0a1c33","eventType":"NEW_IOC",\
            "title":"新增 IOC:198.51.100.7","body":"型別 IPV4,TLP CLEAR","severity":"MEDIUM",\
            "resourceType":"indicator","resourceId":"3f4a1c0e-2b7d-4f10-9c11-8a2e5d6b7c90",\
            "read":false,"createdAt":"2026-08-29T09:15:04Z"}],"nextCursor":null,"hasMore":false}""";

    @Operation(
            summary = "List notifications",
            description = "Cursor-paginated notifications visible to the caller: the caller's own tenant plus "
                    + "platform-wide notifications, and within those either broadcasts or notifications addressed "
                    + "to the caller. 認證:需要 Bearer JWT 或 X-API-Key,權限 notification:read。")
    @ApiResponse(
            responseCode = "200",
            description = "One page of notifications, newest first",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PageResponse.class),
                            examples = @ExampleObject(value = PAGE_EXAMPLE)))
    @ApiResponse(responseCode = "400", description = "Cursor cannot be parsed (INVALID_CURSOR)")
    @ApiResponse(responseCode = "403", description = "Missing notification:read permission (FORBIDDEN)")
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "apiKeyAuth")
    PageResponse<NotificationDto> listNotifications(String cursor, Integer limit, boolean unreadOnly);

    @Operation(
            summary = "Mark a notification as read",
            description = "Marks one notification as read. A notification outside the caller's visible scope is "
                    + "reported as 404 rather than 403 so that existence is not disclosed. "
                    + "認證:需要 Bearer JWT 或 X-API-Key,權限 notification:read。")
    @ApiResponse(
            responseCode = "204",
            description = "Marked as read",
            content =
                    @Content(mediaType = "application/json", examples = @ExampleObject(name = "empty", value = "null")))
    @ApiResponse(
            responseCode = "404",
            description = "No such notification in the caller's scope, or it was already read (NOT_FOUND)")
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "apiKeyAuth")
    ResponseEntity<Void> markRead(UUID id);
}
