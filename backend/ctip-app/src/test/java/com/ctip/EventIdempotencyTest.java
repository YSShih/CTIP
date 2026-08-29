package com.ctip;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctip.application.identity.AuthService;
import com.ctip.application.identity.AuthSession;
import com.ctip.application.notification.NotificationService;
import com.ctip.application.notification.WebhookManagementService;
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
import com.ctip.domain.notification.NotificationEvent;
import com.ctip.domain.notification.NotificationType;
import com.ctip.domain.notification.WebhookFilter;
import com.ctip.domain.plan.PlanCode;
import com.ctip.domain.source.SourceId;
import com.ctip.domain.tenant.TenantId;
import com.ctip.sdk.RedistributionPolicy;
import com.ctip.sdk.Severity;
import com.ctip.sdk.Tlp;
import com.ctip.support.IndicatorFixtures;
import com.ctip.support.RecordingWebhookSender;
import com.ctip.support.TestIdentities;
import com.ctip.support.TestPlans;
import com.ctip.support.WebhookFixtures;
import com.ctip.support.WebhookTestConfig;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * DoD M3-03:消費端冪等——<strong>同一個 {@code eventId} 重送不產生重複副作用</strong>
 * (docs/spec/13-platform-ops.md §13.1 規則 5)。
 *
 * <p>去重鍵是 {@code eventId},落點是兩個唯一索引({@code ux_notif_idempotent}、
 * {@code ux_wd_idempotent}),不是記憶體裡的一個 Set——重啟後仍然有效。
 *
 * <p>本測試同時涵蓋 §13.1 規則 6:事件在 <strong>AFTER_COMMIT</strong> 才送達消費端
 * (見 {@link #theInProcessForwarderRunsAfterCommitAndItsWritesLand})。
 */
@Import(WebhookTestConfig.class)
class EventIdempotencyTest extends AbstractPostgresIntegrationTest {

    private static final IndicatorId INDICATOR =
            new IndicatorId(UUID.fromString("5c0ffee0-0000-4000-8000-00000000e1d0"));

    @Autowired
    private NotificationService notifications;

    @Autowired
    private WebhookManagementService webhooks;

    @Autowired
    private RecordingWebhookSender sender;

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
        sender.reset();
    }

    @Test
    void resendingTheSameEventIdProducesNoDuplicateNotificationOrDelivery() {
        WebhookFixtures.Owner tenant = premiumTenant("idem-basic");
        WebhookManagementService.IssuedWebhook hook = WebhookFixtures.register(
                webhooks, tenant, "idem", Set.of(NotificationType.NEW_IOC), WebhookFilter.unfiltered());

        UUID eventId = idGenerator.nextId();
        NotificationEvent event = WebhookFixtures.newIoc(eventId, tenant.tenantId(), Severity.HIGH, Set.of(), Set.of());

        notifications.dispatch(event);
        notifications.dispatch(event);
        notifications.dispatch(event);

        assertThat(notificationRows(eventId)).isEqualTo(1);
        assertThat(deliveryRows(hook, eventId)).isEqualTo(1);
        assertThat(sender.requests().stream()
                        .filter(request -> request.eventId().equals(eventId))
                        .count())
                .isEqualTo(1);
    }

    /**
     * 重送不得讓已經停用的重試「復活」:第二次 dispatch 依然撞上 attempt=1 的唯一鍵,
     * 不會產生第二條重試鏈。
     */
    @Test
    void resendingAFailedEventDoesNotStartASecondRetryChain() {
        WebhookFixtures.Owner tenant = premiumTenant("idem-retry");
        WebhookManagementService.IssuedWebhook hook = WebhookFixtures.register(
                webhooks, tenant, "idem-retry", Set.of(NotificationType.NEW_IOC), WebhookFilter.unfiltered());
        sender.failAlways();

        UUID eventId = idGenerator.nextId();
        NotificationEvent event = WebhookFixtures.newIoc(eventId, tenant.tenantId(), Severity.LOW, Set.of(), Set.of());
        notifications.dispatch(event);
        notifications.dispatch(event);

        assertThat(deliveryRows(hook, eventId)).isEqualTo(1);
        assertThat(jdbc.queryForList(
                        "select attempt from webhook_deliveries where webhook_id = ? and event_id = ?",
                        Integer.class,
                        hook.webhook().id().value(),
                        eventId))
                .containsExactly(1);
    }

    /**
     * 真實路徑:{@code EventPublisherPort.publish} → AFTER_COMMIT → 程序內轉發 → 通知落庫。
     *
     * <p>兩件事一起驗:(1) 交易還沒提交時消費端不得看到事件(§13.1 規則 6);
     * (2) 提交之後消費端的寫入確實落庫。
     *
     * <p>消費端一律 {@code REQUIRES_NEW}(02 §2.4 的規則)。<strong>實測補充</strong>
     * (2026-08-29,ADR 0029):把它改回預設的 {@code REQUIRED},本測試<strong>仍然通過</strong>
     * ——afterCommit 回呼期間連線尚未歸還,寫入會在歸還(還原 autoCommit)時一併提交。
     * 所以這條斷言證明的是「落庫」,不是「REQUIRES_NEW 不可省」;
     * {@code REQUIRES_NEW} 保留的理由是它讓寫入有自己明確的提交邊界,
     * 而不是依賴連線歸還的副作用。
     */
    @Test
    void theInProcessForwarderRunsAfterCommitAndItsWritesLand() {
        WebhookFixtures.Owner tenant = premiumTenant("idem-aftercommit");
        seedIndicator(tenant.tenantId());

        transactions.executeWithoutResult(status -> {
            events.publish(new IndicatorEvents.IndicatorCreated(
                    INDICATOR,
                    tenant.tenantId(),
                    com.ctip.sdk.IocType.DOMAIN,
                    "aftercommit.ctip-sample.net",
                    Tlp.CLEAR));
            // 交易內:AFTER_COMMIT 尚未觸發,消費端不該看到任何東西
            assertThat(jdbc.queryForObject(
                            "select count(*) from notifications where tenant_id = ?",
                            Integer.class,
                            tenant.tenantId().value()))
                    .isZero();
        });

        List<String> titles = jdbc.queryForList(
                "select title from notifications where tenant_id = ? and event_type = 'NEW_IOC'",
                String.class,
                tenant.tenantId().value());
        assertThat(titles).hasSize(1);
        assertThat(titles.get(0)).contains("aftercommit.ctip-sample.net");
    }

    /** 補齊的過濾欄位真的來自聚合:severity 與 tags 在事件上並不存在(ADR 0029)。 */
    @Test
    void theNotificationProjectionIsEnrichedFromTheAggregate() {
        WebhookFixtures.Owner tenant = premiumTenant("idem-enrich");
        seedIndicator(tenant.tenantId());
        WebhookManagementService.IssuedWebhook hook = WebhookFixtures.register(
                webhooks,
                tenant,
                "enriched",
                Set.of(NotificationType.NEW_IOC),
                // IndicatorFixtures 的來源記錄是 MEDIUM + tag security-test
                new WebhookFilter(Set.of(), Severity.MEDIUM, Set.of("security-test"), Set.of()));

        transactions.executeWithoutResult(status -> events.publish(new IndicatorEvents.IndicatorCreated(
                INDICATOR, tenant.tenantId(), com.ctip.sdk.IocType.DOMAIN, "enrich.ctip-sample.net", Tlp.CLEAR)));

        assertThat(sender.requests().stream()
                        .filter(request ->
                                request.targetUrl().equals(hook.webhook().targetUrl()))
                        .toList())
                .hasSize(1);
    }

    private void seedIndicator(TenantId tenantId) {
        if (indicators.findById(INDICATOR).isPresent()) {
            return;
        }
        IndicatorFixtures.upsert(
                indicators,
                anySourceId(),
                new IndicatorFixtures.Fixture(
                        INDICATOR, tenantId, Tlp.CLEAR, RedistributionPolicy.PUBLIC_REDISTRIBUTABLE, "idempotency"));
    }

    private SourceId anySourceId() {
        return new SourceId(jdbc.queryForObject("select id from sources limit 1", UUID.class));
    }

    private int notificationRows(UUID eventId) {
        Integer count =
                jdbc.queryForObject("select count(*) from notifications where event_id = ?", Integer.class, eventId);
        return count == null ? 0 : count;
    }

    private int deliveryRows(WebhookManagementService.IssuedWebhook issued, UUID eventId) {
        Integer count = jdbc.queryForObject(
                "select count(*) from webhook_deliveries where webhook_id = ? and event_id = ?",
                Integer.class,
                issued.webhook().id().value(),
                eventId);
        return count == null ? 0 : count;
    }

    private WebhookFixtures.Owner premiumTenant(String slug) {
        AuthSession session = identities.register(slug + "@example.org", RoleCode.TENANT_ADMIN);
        TenantId tenantId = session.identity().tenantId();
        testPlans.assign(tenantId, PlanCode.PREMIUM);
        return new WebhookFixtures.Owner(tenantId, session.identity().userId());
    }
}
