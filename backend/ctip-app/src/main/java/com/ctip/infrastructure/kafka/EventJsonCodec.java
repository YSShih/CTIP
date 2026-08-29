package com.ctip.infrastructure.kafka;

import com.ctip.domain.notification.NotificationEvent;
import com.ctip.infrastructure.event.DomainEventEnvelope;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 事件的線上 JSON 格式(docs/api/events/ 的 schema;13 §13.1 規則 1–4)。
 *
 * <p>自建 {@link ObjectMapper} 而非注入 Boot 的那一顆:那一顆的設定服務於 REST API,
 * 而事件是另一份對外契約——REST 的序列化偏好改動不得改變 Kafka 上的位元組
 * (同 {@code PlanCacheCodec}、{@code JsonPayloads} 的作法)。
 *
 * <p>信封欄位 {@code eventId / eventType / occurredAt / tenantId / traceId} 為 §13.1 規則 4 的強制欄位;
 * 領域內容一律放在 {@code payload} 之下,識別碼值物件由 {@link ValueObjectJsonModule} 展開成字串。
 */
@Component
public class EventJsonCodec {

    private final ObjectMapper mapper =
            JsonMapper.builder().addModule(ValueObjectJsonModule.create()).build();

    /** 領域事件信封 → topic payload。 */
    public String encode(DomainEventEnvelope envelope) {
        ObjectNode root = mapper.createObjectNode();
        root.put("eventId", envelope.eventId().toString());
        root.put("eventType", envelope.event().eventType());
        root.put("occurredAt", envelope.occurredAt().toString());
        root.put("tenantId", envelope.event().tenantId().value().toString());
        root.put("traceId", envelope.traceId());
        root.set("payload", mapper.valueToTree(envelope.event()));
        return mapper.writeValueAsString(root);
    }

    /** 通知投影 → {@code ctip.notification.events.v1} 的 payload。 */
    public String encode(NotificationEvent event) {
        ObjectNode root = mapper.createObjectNode();
        root.put("eventId", event.eventId().toString());
        root.put("eventType", event.type().name());
        root.put("occurredAt", event.occurredAt().toString());
        root.put("tenantId", event.tenantId().value().toString());
        root.put("traceId", event.traceId());
        root.put("title", event.title());
        root.put("body", event.body());
        root.put("severity", event.severity().name());
        root.put("resourceType", event.resourceType());
        root.put(
                "resourceId",
                event.resourceId() == null ? null : event.resourceId().toString());
        root.put("userId", event.userId() == null ? null : event.userId().toString());
        putStrings(
                root.putArray("iocTypes"),
                event.iocTypes().stream().map(Enum::name).toList());
        putStrings(root.putArray("tags"), event.tags().stream().sorted().toList());
        putStrings(
                root.putArray("sourceIds"),
                event.sourceIds().stream().map(UUID::toString).sorted().toList());
        return mapper.writeValueAsString(root);
    }

    public NotificationEvent decodeNotification(String json) {
        JsonNode root = mapper.readTree(json);
        return new NotificationEvent(
                UUID.fromString(root.get("eventId").asString()),
                com.ctip.domain.notification.NotificationType.valueOf(
                        root.get("eventType").asString()),
                new com.ctip.domain.tenant.TenantId(
                        UUID.fromString(root.get("tenantId").asString())),
                Instant.parse(root.get("occurredAt").asString()),
                text(root, "traceId"),
                root.get("title").asString(),
                text(root, "body"),
                com.ctip.sdk.Severity.valueOf(root.get("severity").asString()),
                text(root, "resourceType"),
                uuid(root, "resourceId"),
                uuid(root, "userId"),
                readSet(root, "iocTypes", com.ctip.sdk.IocType::valueOf),
                readSet(root, "tags", value -> value),
                readSet(root, "sourceIds", UUID::fromString));
    }

    private static void putStrings(ArrayNode array, java.util.List<String> values) {
        values.forEach(array::add);
    }

    private static String text(JsonNode root, String field) {
        JsonNode node = root.get(field);
        return node == null || node.isNull() ? null : node.asString();
    }

    private static UUID uuid(JsonNode root, String field) {
        String value = text(root, field);
        return value == null ? null : UUID.fromString(value);
    }

    private static <T> Set<T> readSet(JsonNode root, String field, java.util.function.Function<String, T> parser) {
        Set<T> values = new LinkedHashSet<>();
        JsonNode array = root.get(field);
        if (array != null) {
            array.forEach(node -> values.add(parser.apply(node.asString())));
        }
        return values;
    }
}
