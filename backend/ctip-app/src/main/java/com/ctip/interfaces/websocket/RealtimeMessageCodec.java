package com.ctip.interfaces.websocket;

import com.ctip.application.notification.NotificationRecord;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 推播訊息的線上格式(09 §9.1「即時推送」:
 * {@code { "type": <NotificationType>, "payload": {...}, "eventId": "..." }})。
 * WebSocket 與 SSE 共用同一份格式——SSE 是 fallback,不是另一套協定。
 */
@Component
public class RealtimeMessageCodec {

    private final ObjectMapper mapper = JsonMapper.builder().build();

    public String encode(NotificationRecord notification) {
        ObjectNode root = mapper.createObjectNode();
        root.put("type", notification.eventType().name());
        root.put("eventId", notification.eventId().toString());
        ObjectNode payload = root.putObject("payload");
        payload.put("id", notification.id().toString());
        payload.put("title", notification.title());
        payload.put("body", notification.body());
        payload.put("severity", notification.severity().name());
        payload.put("resourceType", notification.resourceType());
        payload.put(
                "resourceId",
                notification.resourceId() == null
                        ? null
                        : notification.resourceId().toString());
        payload.put("createdAt", notification.createdAt().toString());
        return mapper.writeValueAsString(root);
    }
}
