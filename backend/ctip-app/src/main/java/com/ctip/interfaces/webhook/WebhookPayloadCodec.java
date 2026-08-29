package com.ctip.interfaces.webhook;

import com.ctip.application.notification.NotificationRecord;
import com.ctip.application.port.WebhookPayloadPort;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 送達 body 的線上格式。
 *
 * <p>欄位順序寫死且不依賴任何全域 mapper 設定:body 是簽章的一部分,而重試會在數分鐘後
 * 重新組裝同一個事件——欄位順序漂移會讓接收端第二次驗簽失敗。
 * 自建 {@link ObjectMapper} 的理由與 {@code EventJsonCodec} 相同:REST 的序列化偏好
 * 不得改變 webhook 的位元組。
 */
@Component
class WebhookPayloadCodec implements WebhookPayloadPort {

    private final ObjectMapper mapper = JsonMapper.builder().build();

    @Override
    public byte[] body(NotificationRecord notification) {
        ObjectNode root = mapper.createObjectNode();
        root.put("eventId", notification.eventId().toString());
        root.put("eventType", notification.eventType().name());
        root.put("occurredAt", notification.createdAt().toString());
        root.put("tenantId", notification.tenantId().value().toString());
        root.put("title", notification.title());
        root.put("body", notification.body());
        root.put("severity", notification.severity().name());
        root.put("resourceType", notification.resourceType());
        root.put(
                "resourceId",
                notification.resourceId() == null
                        ? null
                        : notification.resourceId().toString());
        return mapper.writeValueAsString(root).getBytes(StandardCharsets.UTF_8);
    }
}
