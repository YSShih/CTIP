package com.ctip.interfaces.rest.openapi;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 即時推送的 OpenAPI 文件(§9.1「即時推送」)。
 *
 * <p>只涵蓋 SSE 的 {@code GET /api/v1/events};{@code GET /api/v1/ws} 是 WebSocket 升級,
 * 不是 HTTP operation,OpenAPI 3.1 沒有它的表達方式——協定與認證方式寫在下方的說明與
 * 09 §9.1 的表格內。
 */
@Tag(name = "Realtime", description = "Server-sent notification stream (fallback for WebSocket).")
public interface RealtimeApi {

    String STREAM_EXAMPLE = """
            event: notification
            data: {"type":"NEW_IOC","eventId":"6f1d2f52-6f0a-4a6f-9a0f-2f1b6d0a1c33",\
            "payload":{"id":"…","title":"新增 IOC:198.51.100.7","severity":"MEDIUM"}}

            : keepalive
            """;

    @Operation(
            summary = "Subscribe to the notification stream (SSE)",
            description = "text/event-stream fallback for clients that cannot use the WebSocket endpoint at "
                    + "GET /api/v1/ws. Messages have the same shape on both transports; a `: keepalive` comment "
                    + "line is sent every 30 seconds. The connection is bound to the caller's tenant at "
                    + "subscription time and cannot be redirected to another tenant. "
                    + "The WebSocket endpoint carries its token in `Sec-WebSocket-Protocol: ctip.auth.<jwt>` "
                    + "because the browser API cannot set headers; this endpoint uses the ordinary header. "
                    + "認證:需要 Bearer JWT 或 X-API-Key,權限 notification:read,且方案必須開啟 websocket_enabled。")
    @ApiResponse(
            responseCode = "200",
            description = "An open event stream",
            content =
                    @Content(
                            mediaType = "text/event-stream",
                            schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = STREAM_EXAMPLE)))
    @ApiResponse(responseCode = "403", description = "Missing notification:read, or the plan has no realtime push")
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "apiKeyAuth")
    SseEmitter subscribe();
}
