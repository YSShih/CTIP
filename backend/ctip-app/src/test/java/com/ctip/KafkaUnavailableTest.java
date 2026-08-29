package com.ctip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.ctip.sdk.IocType;
import com.ctip.sdk.RedistributionPolicy;
import com.ctip.sdk.Tlp;
import com.ctip.support.IndicatorFixtures;
import com.ctip.support.TestIdentities;
import com.ctip.support.TestPlans;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * DoD M3-04:<strong>Kafka 不可用時業務操作不失敗</strong>(docs/spec/13-platform-ops.md §13.1 規則 7)。
 *
 * <p>設定成 {@code NOTIFICATION_TRANSPORT=kafka} 但把 bootstrap 指向一個<strong>不存在的</strong>
 * broker(保留給文件用途的 TEST-NET-1 位址,不會意外連到任何東西)。三件事必須成立:
 * <ul>
 *   <li>應用照常啟動——broker 不在不得使 context refresh 失敗</li>
 *   <li>讀取端點照常回 200</li>
 *   <li>會發事件的寫入操作照常成功並落庫;轉發失敗只記錄</li>
 * </ul>
 *
 * <p>不是 L4:這裡要的是「broker 不存在」,起一個容器再停掉反而更慢也更不穩定。
 */
@AutoConfigureMockMvc
@SpringBootTest(
        properties = {
            "ctip.notification.transport=kafka",
            // 192.0.2.0/24 是 RFC 5737 的 TEST-NET-1,保證不可路由
            "spring.kafka.bootstrap-servers=192.0.2.1:9092",
            // 送出失敗要快速放棄,否則每個事件會卡住預設的 60 秒 delivery timeout
            "spring.kafka.producer.properties.max.block.ms=500",
            "spring.kafka.producer.properties.delivery.timeout.ms=1000",
            "spring.kafka.producer.properties.request.timeout.ms=500",
            "spring.kafka.producer.properties.retries=0",
            "spring.kafka.admin.fail-fast=false"
        })
class KafkaUnavailableTest extends AbstractPostgresIntegrationTest {

    private static final String CLIENT_IP = "10.70.0.11";

    private static final IndicatorId INDICATOR =
            new IndicatorId(UUID.fromString("5c0ffee0-0000-4000-8000-00000000dead"));

    @Autowired
    private MockMvc mvc;

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

    /** context 已經載入到能注入這些 bean,就代表沒有 broker 也啟動得起來。 */
    @Test
    void theApplicationStartsWithoutABroker() {
        assertThat(events).isNotNull();
        assertThat(mvc).isNotNull();
    }

    @Test
    void readEndpointsStillAnswer() throws Exception {
        mvc.perform(get("/api/v1/iocs?limit=1").with(fromClient())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/health").with(fromClient())).andExpect(status().isOk());
    }

    /** 會發事件的業務操作:提交一筆 IOC。轉發到 Kafka 必然失敗,而端點必須照樣回 201。 */
    @Test
    void aWriteThatPublishesAnEventStillSucceedsAndPersists() throws Exception {
        AuthSession session = identities.register("kafka-down@example.org", RoleCode.TENANT_ADMIN);
        testPlans.assign(session.identity().tenantId(), PlanCode.PREMIUM);

        mvc.perform(post("/api/v1/iocs")
                        .with(fromClient())
                        .header("Authorization", TestIdentities.bearer(session))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"DOMAIN","value":"kafka-down.ctip-sample.net","severity":"MEDIUM",\
                                "confidence":60,"tlp":"AMBER","tags":["kafka-down"]}"""))
                .andExpect(status().isCreated());

        Integer stored = jdbc.queryForObject(
                "select count(*) from indicators where normalized_value = ?",
                Integer.class,
                "kafka-down.ctip-sample.net");
        assertThat(stored).isEqualTo(1);
    }

    /** 直接對發佈端施壓:轉發 listener 丟出去的任何東西都不得讓交易失敗。 */
    @Test
    void publishingADomainEventDoesNotThrowWhenTheBrokerIsUnreachable() {
        TenantId tenantId = identities
                .register("kafka-down-publish@example.org", RoleCode.TENANT_ADMIN)
                .identity()
                .tenantId();
        seedIndicator(tenantId);

        org.assertj.core.api.Assertions.assertThatCode(() ->
                        transactions.executeWithoutResult(status -> events.publish(new IndicatorEvents.IndicatorCreated(
                                INDICATOR, tenantId, IocType.DOMAIN, "unreachable.ctip-sample.net", Tlp.CLEAR))))
                .doesNotThrowAnyException();
    }

    private void seedIndicator(TenantId tenantId) {
        if (indicators.findById(INDICATOR).isPresent()) {
            return;
        }
        IndicatorFixtures.upsert(
                indicators,
                new SourceId(jdbc.queryForObject("select id from sources order by display_name limit 1", UUID.class)),
                new IndicatorFixtures.Fixture(
                        INDICATOR, tenantId, Tlp.CLEAR, RedistributionPolicy.PUBLIC_REDISTRIBUTABLE, "kafkadown"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor fromClient() {
        return request -> {
            request.setRemoteAddr(CLIENT_IP);
            return request;
        };
    }
}
