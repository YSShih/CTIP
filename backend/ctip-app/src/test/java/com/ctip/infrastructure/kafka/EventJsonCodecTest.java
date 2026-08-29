package com.ctip.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctip.domain.event.IndicatorEvents;
import com.ctip.domain.event.SourceEvents;
import com.ctip.domain.event.WebhookEvents;
import com.ctip.domain.indicator.IndicatorId;
import com.ctip.domain.notification.NotificationEvent;
import com.ctip.domain.notification.NotificationType;
import com.ctip.domain.notification.WebhookId;
import com.ctip.domain.source.SourceId;
import com.ctip.domain.tenant.TenantId;
import com.ctip.infrastructure.event.DomainEventEnvelope;
import com.ctip.sdk.IocType;
import com.ctip.sdk.Severity;
import com.ctip.sdk.Tlp;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * 事件的線上 JSON 格式(docs/api/events/ 的 schema;13 §13.1 規則 1–4)。
 * schema 是對外契約,欄位名與型別的漂移必須被測試擋下。
 */
@Tag("unit")
class EventJsonCodecTest {

    private static final Instant OCCURRED = Instant.parse("2026-08-29T09:15:04Z");
    private static final UUID EVENT_ID = UUID.fromString("6f1d2f52-6f0a-4a6f-9a0f-2f1b6d0a1c33");
    private static final TenantId TENANT = new TenantId(UUID.fromString("11111111-2222-4333-8444-555555555555"));

    private final EventJsonCodec codec = new EventJsonCodec();

    @Test
    void theEnvelopeCarriesTheFiveMandatoryFields() {
        String json = codec.encode(new DomainEventEnvelope(
                EVENT_ID,
                OCCURRED,
                "trace-abc",
                new IndicatorEvents.IndicatorCreated(
                        new IndicatorId(new UUID(0, 42)), TENANT, IocType.IPV4, "198.51.100.7", Tlp.CLEAR)));
        var node = JsonMapper.builder().build().readTree(json);

        assertThat(node.get("eventId").asString()).isEqualTo(EVENT_ID.toString());
        assertThat(node.get("eventType").asString()).isEqualTo("IndicatorCreated");
        assertThat(node.get("occurredAt").asString()).isEqualTo("2026-08-29T09:15:04Z");
        assertThat(node.get("tenantId").asString()).isEqualTo(TENANT.value().toString());
        assertThat(node.get("traceId").asString()).isEqualTo("trace-abc");
    }

    /**
     * 識別碼值物件在線上必須是字串。沒有 {@code ValueObjectJsonModule} 的話它們會變成
     * {@code {"value":"…"}} ——Java 的包裝型別不該漏到對外契約上。
     */
    @Test
    void identifierValueObjectsSerialiseAsPlainStrings() {
        String json = codec.encode(new DomainEventEnvelope(
                EVENT_ID,
                OCCURRED,
                null,
                new IndicatorEvents.IndicatorRevoked(
                        new IndicatorId(new UUID(0, 42)), TENANT, new SourceId(new UUID(0, 43)))));
        var node = JsonMapper.builder().build().readTree(json);

        assertThat(node.at("/payload/indicatorId").isString()).isTrue();
        assertThat(node.at("/payload/revokedBy").isString()).isTrue();
        assertThat(node.at("/payload/indicatorId").asString()).isEqualTo(new UUID(0, 42).toString());
    }

    @Test
    void webhookAndSourceIdentifiersAreCoveredToo() {
        String json = codec.encode(new DomainEventEnvelope(
                EVENT_ID,
                OCCURRED,
                null,
                new WebhookEvents.WebhookDisabled(TENANT, new WebhookId(new UUID(0, 44)), 5)));
        assertThat(JsonMapper.builder()
                        .build()
                        .readTree(json)
                        .at("/payload/webhookId")
                        .isString())
                .isTrue();

        String health = codec.encode(new DomainEventEnvelope(
                EVENT_ID, OCCURRED, null, new SourceEvents.SourceFailed(new SourceId(new UUID(0, 45)), 7)));
        assertThat(JsonMapper.builder()
                        .build()
                        .readTree(health)
                        .at("/payload/sourceId")
                        .isString())
                .isTrue();
    }

    /** 通知投影必須可以完整往返:Kafka 的消費端就是靠它重建事件。 */
    @Test
    void theNotificationProjectionRoundTrips() {
        NotificationEvent original = new NotificationEvent(
                EVENT_ID,
                NotificationType.NEW_IOC,
                TENANT,
                OCCURRED,
                "trace-xyz",
                "新增 IOC:198.51.100.7",
                "型別 IPV4",
                Severity.HIGH,
                "indicator",
                new UUID(0, 42),
                new UUID(0, 43),
                Set.of(IocType.IPV4),
                Set.of("botnet", "c2"),
                Set.of(new UUID(0, 44)));

        assertThat(codec.decodeNotification(codec.encode(original))).isEqualTo(original);
    }

    /** 可為 null 的欄位往返後仍是 null,不得變成字串 "null"。 */
    @Test
    void nullableFieldsSurviveTheRoundTrip() {
        NotificationEvent sparse = new NotificationEvent(
                EVENT_ID,
                NotificationType.SYSTEM_ALERT,
                TenantId.PUBLIC,
                OCCURRED,
                null,
                "系統警示",
                null,
                Severity.INFO,
                null,
                null,
                null,
                Set.of(),
                Set.of(),
                Set.of());
        NotificationEvent decoded = codec.decodeNotification(codec.encode(sparse));

        assertThat(decoded.traceId()).isNull();
        assertThat(decoded.body()).isNull();
        assertThat(decoded.resourceType()).isNull();
        assertThat(decoded.resourceId()).isNull();
        assertThat(decoded.userId()).isNull();
    }
}
