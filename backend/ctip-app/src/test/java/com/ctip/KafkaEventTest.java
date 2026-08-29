package com.ctip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.ctip.application.identity.AuthService;
import com.ctip.application.identity.AuthSession;
import com.ctip.application.port.ClockPort;
import com.ctip.application.port.EventPublisherPort;
import com.ctip.application.port.IdGeneratorPort;
import com.ctip.application.port.IndicatorRepository;
import com.ctip.application.port.PlanRepository;
import com.ctip.application.port.SubscriptionRepository;
import com.ctip.application.port.TenantMembershipRepository;
import com.ctip.application.rbac.RoleCode;
import com.ctip.domain.event.IndicatorEvents;
import com.ctip.domain.indicator.IndicatorId;
import com.ctip.domain.plan.PlanCode;
import com.ctip.domain.source.SourceId;
import com.ctip.domain.tenant.TenantId;
import com.ctip.infrastructure.kafka.KafkaTopics;
import com.ctip.sdk.IocType;
import com.ctip.sdk.RedistributionPolicy;
import com.ctip.sdk.Tlp;
import com.ctip.support.IndicatorFixtures;
import com.ctip.support.KafkaTestContainer;
import com.ctip.support.TestIdentities;
import com.ctip.support.TestPlans;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * DoD M3-02:Kafka(KRaft)啟動,事件正確發佈與消費(docs/spec/13-platform-ops.md §13.1)。
 * L4(heavy):真的起一個 {@code apache/kafka:4.2.1} broker(14 §14.1)。
 *
 * <p>三件事:
 * <ul>
 *   <li>domain event 進到<strong>對的 topic</strong>,且 payload 含 §13.1 規則 4 的五個信封欄位</li>
 *   <li>識別碼值物件在線上是字串,不是 {@code {"value": …}} ——事件 schema 是對外契約</li>
 *   <li>通知投影經 {@code ctip.notification.events.v1} 被<strong>消費</strong>,副作用真的發生</li>
 * </ul>
 *
 * <p>發佈端完全沒有被修改:測試用的是 {@code EventPublisherPort},與 M1 起的路徑相同。
 */
@Tag("heavy")
@SpringBootTest(properties = "ctip.notification.transport=kafka")
class KafkaEventTest extends AbstractPostgresIntegrationTest {

    private static final IndicatorId INDICATOR =
            new IndicatorId(UUID.fromString("5c0ffee0-0000-4000-8000-0000000ca0fa"));

    @DynamicPropertySource
    static void kafka(DynamicPropertyRegistry registry) {
        registry.add("KAFKA_BOOTSTRAP_SERVERS", KafkaTestContainer::bootstrapServers);
    }

    @Autowired
    private EventPublisherPort events;

    @Autowired
    private IndicatorRepository indicators;

    @Autowired
    private AuthService authService;

    @Autowired
    private TenantMembershipRepository memberships;

    @Autowired
    private PlanRepository plans;

    @Autowired
    private SubscriptionRepository subscriptions;

    @Autowired
    private IdGeneratorPort idGenerator;

    @Autowired
    private ClockPort clock;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transactions;

    private TestIdentities identities;
    private TestPlans testPlans;

    @BeforeEach
    void setUp() {
        identities = new TestIdentities(authService, memberships);
        testPlans = new TestPlans(plans, subscriptions, idGenerator, clock);
    }

    /**
     * 六個 topic 由 {@code KafkaAdmin} 在啟動時建立,而且是<strong>我們宣告的分割數</strong>
     * ——只斷言「topic 存在」會被 broker 的 auto-create 蒙混過去(publish 到不存在的 topic
     * 會自動建一個分割數為 broker 預設值的 topic)。
     */
    @Test
    void allSixTopicsAreDeclaredWithTheConfiguredPartitionCount() {
        try (org.apache.kafka.clients.admin.Admin admin = org.apache.kafka.clients.admin.Admin.create(
                Map.of("bootstrap.servers", KafkaTestContainer.bootstrapServers()))) {
            await().atMost(Duration.ofSeconds(30))
                    .untilAsserted(
                            () -> assertThat(admin.listTopics().names().get()).containsAll(KafkaTopics.ALL));
            var described =
                    admin.describeTopics(KafkaTopics.ALL).allTopicNames().get();
            assertThat(described.keySet()).containsAll(KafkaTopics.ALL);
            described.forEach((name, description) ->
                    assertThat(description.partitions()).as("%s 的分割數", name).hasSize(3));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("列出 topic 被中斷", e);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new IllegalStateException("列出 topic 失敗", e);
        }
    }

    @Test
    void anIndicatorEventReachesItsDomainTopicWithTheMandatoryEnvelopeFields() {
        TenantId tenantId = premiumTenant("kafka-domain");
        seedIndicator(tenantId);

        transactions.executeWithoutResult(status -> events.publish(new IndicatorEvents.IndicatorCreated(
                INDICATOR, tenantId, IocType.DOMAIN, "kafka.ctip-sample.net", Tlp.CLEAR)));

        JsonNode payload = awaitRecord(
                KafkaTopics.INDICATOR_UPDATED,
                node -> node.path("payload")
                        .path("normalizedValue")
                        .asString("")
                        .equals("kafka.ctip-sample.net"));

        // §13.1 規則 4:每個事件含 eventId、eventType、occurredAt、tenantId、traceId
        assertThat(payload.get("eventId").asString()).isNotBlank();
        assertThat(payload.get("eventType").asString()).isEqualTo("IndicatorCreated");
        assertThat(payload.get("occurredAt").asString()).isNotBlank();
        assertThat(payload.get("tenantId").asString())
                .isEqualTo(tenantId.value().toString());
        assertThat(payload.has("traceId")).isTrue();

        // 識別碼在線上是字串,不是 {"value": …};JPA entity 也不得成為 payload(規則 2)
        assertThat(payload.at("/payload/indicatorId").isString()).isTrue();
        assertThat(payload.at("/payload/indicatorId").asString())
                .isEqualTo(INDICATOR.value().toString());
        assertThat(payload.at("/payload/tlp").asString()).isEqualTo("CLEAR");
    }

    @Test
    void theNotificationProjectionIsPublishedAndConsumed() {
        TenantId tenantId = premiumTenant("kafka-notify");
        seedIndicator(tenantId);

        transactions.executeWithoutResult(status -> events.publish(new IndicatorEvents.IndicatorCreated(
                INDICATOR, tenantId, IocType.DOMAIN, "kafka-notify.ctip-sample.net", Tlp.CLEAR)));

        // 發佈面
        JsonNode projection = awaitRecord(
                KafkaTopics.NOTIFICATION_EVENTS,
                node -> node.path("title").asString("").contains("kafka-notify.ctip-sample.net"));
        assertThat(projection.get("eventType").asString()).isEqualTo("NEW_IOC");

        // 消費面:@KafkaListener 把它變成一列站內通知(冪等鍵為 eventId)
        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(jdbc.queryForObject(
                                "select count(*) from notifications where tenant_id = ? and event_id = ?",
                                Integer.class,
                                tenantId.value(),
                                UUID.fromString(projection.get("eventId").asString())))
                        .isEqualTo(1));
    }

    /** 攝取與來源健康事件各有自己的 topic;對應表見 {@code docs/api/events/README.md}。 */
    @Test
    void ingestionAndSourceEventsGoToTheirOwnTopics() {
        transactions.executeWithoutResult(status -> {
            events.publish(new com.ctip.domain.event.IngestionEvents.IngestionStarted(anySourceId()));
            events.publish(new com.ctip.domain.event.SourceEvents.SourceDegraded(anySourceId(), 3));
        });

        assertThat(awaitRecord(
                                KafkaTopics.THREAT_INGEST,
                                node -> node.path("eventType").asString("").equals("IngestionStarted"))
                        .get("tenantId")
                        .asString())
                .isEqualTo(TenantId.PUBLIC.value().toString());
        assertThat(awaitRecord(
                        KafkaTopics.SYSTEM_ALERT,
                        node -> node.path("eventType").asString("").equals("SourceDegraded")))
                .isNotNull();
    }

    /** 從 topic 起點讀,直到出現符合條件的一筆。 */
    private JsonNode awaitRecord(String topic, java.util.function.Predicate<JsonNode> matcher) {
        List<JsonNode> seen = new ArrayList<>();
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KafkaTestContainer.bootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "ctip-test-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(topic));
            long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            while (System.nanoTime() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> record : records) {
                    JsonNode node = JsonMapper.builder().build().readTree(record.value());
                    seen.add(node);
                    if (matcher.test(node)) {
                        return node;
                    }
                }
            }
        }
        throw new AssertionError("topic " + topic + " 在 30 秒內沒有出現符合條件的事件;實際收到 " + seen.size() + " 筆");
    }

    private void seedIndicator(TenantId tenantId) {
        if (indicators.findById(INDICATOR).isPresent()) {
            return;
        }
        IndicatorFixtures.upsert(
                indicators,
                anySourceId(),
                new IndicatorFixtures.Fixture(
                        INDICATOR, tenantId, Tlp.CLEAR, RedistributionPolicy.PUBLIC_REDISTRIBUTABLE, "kafka"));
    }

    private SourceId anySourceId() {
        return new SourceId(jdbc.queryForObject("select id from sources order by display_name limit 1", UUID.class));
    }

    private TenantId premiumTenant(String slug) {
        AuthSession session = identities.register(slug + "@example.org", RoleCode.TENANT_ADMIN);
        TenantId tenantId = session.identity().tenantId();
        testPlans.assign(tenantId, PlanCode.PREMIUM);
        return tenantId;
    }
}
