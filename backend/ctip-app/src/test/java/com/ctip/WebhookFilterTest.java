package com.ctip;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctip.application.identity.AuthService;
import com.ctip.application.identity.AuthSession;
import com.ctip.application.notification.NotificationService;
import com.ctip.application.notification.WebhookManagementService;
import com.ctip.application.notification.WebhookRequest;
import com.ctip.application.port.ClockPort;
import com.ctip.application.port.IdGeneratorPort;
import com.ctip.application.port.PlanRepository;
import com.ctip.application.port.SubscriptionRepository;
import com.ctip.application.port.TenantMembershipRepository;
import com.ctip.application.rbac.RoleCode;
import com.ctip.domain.notification.NotificationEvent;
import com.ctip.domain.notification.NotificationType;
import com.ctip.domain.notification.WebhookFilter;
import com.ctip.domain.plan.PlanCode;
import com.ctip.domain.tenant.TenantId;
import com.ctip.sdk.IocType;
import com.ctip.sdk.Severity;
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

/**
 * DoD M3-08:訂閱過濾<strong>在伺服器端執行</strong>(不變量 W5;docs/spec/13-platform-ops.md §13.2)。
 *
 * <p>「在伺服器端」的可觀察定義是:不符條件的 webhook <strong>完全沒有動靜</strong>
 * ——沒有 HTTP 請求、連 {@code webhook_deliveries} 都不會產生一列。
 * 只斷言「有送的那一個內容正確」證明不了這件事:把全部事件推出去再由 client 篩,
 * 那一條斷言也會通過。
 */
@Import(WebhookTestConfig.class)
class WebhookFilterTest extends AbstractPostgresIntegrationTest {

    private static final UUID SOURCE_A = UUID.fromString("2f1c9d40-0000-4000-8000-00000000000a");
    private static final UUID SOURCE_B = UUID.fromString("2f1c9d40-0000-4000-8000-00000000000b");

    @Autowired
    private WebhookManagementService webhooks;

    @Autowired
    private NotificationService notifications;

    @Autowired
    private RecordingWebhookSender sender;

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

    private TestIdentities identities;
    private TestPlans testPlans;

    @BeforeEach
    void setUp() {
        identities = new TestIdentities(authService, memberships);
        testPlans = new TestPlans(plans, subscriptions, idGenerator, clock);
        sender.reset();
    }

    @Test
    void aWebhookWhoseFilterDoesNotMatchReceivesNothingAtAll() {
        WebhookFixtures.Owner tenant = premiumTenant("filter-scope");
        WebhookManagementService.IssuedWebhook wanted = WebhookFixtures.register(
                webhooks,
                tenant,
                "wants-high-ipv4",
                Set.of(NotificationType.NEW_IOC),
                new WebhookFilter(Set.of(IocType.IPV4), Severity.HIGH, Set.of(), Set.of()));
        WebhookManagementService.IssuedWebhook unwanted = WebhookFixtures.register(
                webhooks,
                tenant,
                "wants-critical-only",
                Set.of(NotificationType.NEW_IOC),
                new WebhookFilter(Set.of(IocType.IPV4), Severity.CRITICAL, Set.of(), Set.of()));

        notifications.dispatch(
                WebhookFixtures.newIoc(idGenerator.nextId(), tenant.tenantId(), Severity.HIGH, Set.of(), Set.of()));

        assertThat(requestsFor(wanted)).hasSize(1);
        assertThat(requestsFor(unwanted)).isEmpty();
        assertThat(deliveryRows(unwanted)).isZero();
    }

    @Test
    void aWebhookThatDidNotSubscribeToTheEventTypeReceivesNothing() {
        WebhookFixtures.Owner tenant = premiumTenant("filter-type");
        WebhookManagementService.IssuedWebhook revokedOnly = WebhookFixtures.register(
                webhooks, tenant, "revoked-only", Set.of(NotificationType.IOC_REVOKED), WebhookFilter.unfiltered());

        notifications.dispatch(
                WebhookFixtures.newIoc(idGenerator.nextId(), tenant.tenantId(), Severity.CRITICAL, Set.of(), Set.of()));

        assertThat(requestsFor(revokedOnly)).isEmpty();
        assertThat(deliveryRows(revokedOnly)).isZero();
    }

    /** 租戶隔離也是伺服器端過濾的一部分:別的租戶的事件不得外洩到本租戶的 webhook。 */
    @Test
    void anotherTenantsEventIsNeverDelivered() {
        WebhookFixtures.Owner listener = premiumTenant("filter-listener");
        WebhookFixtures.Owner other = premiumTenant("filter-other");
        WebhookManagementService.IssuedWebhook hook = WebhookFixtures.register(
                webhooks, listener, "listener", Set.of(NotificationType.NEW_IOC), WebhookFilter.unfiltered());

        notifications.dispatch(
                WebhookFixtures.newIoc(idGenerator.nextId(), other.tenantId(), Severity.HIGH, Set.of(), Set.of()));

        assertThat(requestsFor(hook)).isEmpty();
        assertThat(deliveryRows(hook)).isZero();
    }

    /** 平台範圍的事件(public tenant)對所有租戶可見——與 §7.9 的 {@code IN (current, public)} 同一條規則。 */
    @Test
    void platformWideEventsReachEveryTenantsWebhook() {
        WebhookFixtures.Owner tenant = premiumTenant("filter-platform");
        WebhookManagementService.IssuedWebhook hook = WebhookFixtures.register(
                webhooks, tenant, "platform", Set.of(NotificationType.SOURCE_FAILURE), WebhookFilter.unfiltered());

        notifications.dispatch(new NotificationEvent(
                idGenerator.nextId(),
                NotificationType.SOURCE_FAILURE,
                TenantId.PUBLIC,
                clock.now(),
                null,
                "來源已停用",
                "連續失敗 5 次",
                Severity.HIGH,
                "source",
                SOURCE_A,
                null,
                Set.of(),
                Set.of(),
                Set.of(SOURCE_A)));

        assertThat(requestsFor(hook)).hasSize(1);
    }

    @Test
    void tagAndSourceDimensionsAreIntersections() {
        WebhookFixtures.Owner tenant = premiumTenant("filter-dimensions");
        WebhookManagementService.IssuedWebhook byTag = WebhookFixtures.register(
                webhooks,
                tenant,
                "by-tag",
                Set.of(NotificationType.NEW_IOC),
                new WebhookFilter(Set.of(), null, Set.of("ransomware"), Set.of()));
        WebhookManagementService.IssuedWebhook bySource = WebhookFixtures.register(
                webhooks,
                tenant,
                "by-source",
                Set.of(NotificationType.NEW_IOC),
                new WebhookFilter(Set.of(), null, Set.of(), Set.of(SOURCE_B)));

        notifications.dispatch(WebhookFixtures.newIoc(
                idGenerator.nextId(),
                tenant.tenantId(),
                Severity.LOW,
                Set.of("ransomware", "botnet"),
                Set.of(SOURCE_A)));

        assertThat(requestsFor(byTag)).hasSize(1);
        assertThat(requestsFor(bySource)).isEmpty();
    }

    /** 指定了 IOC 型別的 webhook 不該收到與 IOC 型別無關的平台通知。 */
    @Test
    void anIocTypeFilterExcludesEventsThatCarryNoIocType() {
        WebhookFixtures.Owner tenant = premiumTenant("filter-nonioc");
        WebhookManagementService.IssuedWebhook ipv4Only = WebhookFixtures.register(
                webhooks,
                tenant,
                "ipv4-only",
                Set.of(NotificationType.NEW_IOC, NotificationType.SUBSCRIPTION_CHANGED),
                new WebhookFilter(Set.of(IocType.IPV4), null, Set.of(), Set.of()));

        notifications.dispatch(new NotificationEvent(
                idGenerator.nextId(),
                NotificationType.SUBSCRIPTION_CHANGED,
                tenant.tenantId(),
                clock.now(),
                null,
                "方案已變更",
                "FREE → PREMIUM",
                Severity.INFO,
                "subscription",
                idGenerator.nextId(),
                null,
                Set.of(),
                Set.of(),
                Set.of()));

        assertThat(requestsFor(ipv4Only)).isEmpty();
    }

    private List<WebhookRequest> requestsFor(WebhookManagementService.IssuedWebhook issued) {
        String url = issued.webhook().targetUrl();
        return sender.requests().stream()
                .filter(request -> request.targetUrl().equals(url))
                .toList();
    }

    private int deliveryRows(WebhookManagementService.IssuedWebhook issued) {
        Integer count = jdbc.queryForObject(
                "select count(*) from webhook_deliveries where webhook_id = ?",
                Integer.class,
                issued.webhook().id().value());
        return count == null ? 0 : count;
    }

    private WebhookFixtures.Owner premiumTenant(String slug) {
        AuthSession session = identities.register(slug + "@example.org", RoleCode.TENANT_ADMIN);
        TenantId tenantId = session.identity().tenantId();
        testPlans.assign(tenantId, PlanCode.PREMIUM);
        return new WebhookFixtures.Owner(tenantId, session.identity().userId());
    }
}
