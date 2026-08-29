package com.ctip;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctip.application.identity.AuthService;
import com.ctip.application.identity.AuthSession;
import com.ctip.application.notification.NotificationService;
import com.ctip.application.notification.WebhookDeliveryService;
import com.ctip.application.notification.WebhookManagementService;
import com.ctip.application.notification.WebhookRequest;
import com.ctip.application.port.ClockPort;
import com.ctip.application.port.IdGeneratorPort;
import com.ctip.application.port.PlanRepository;
import com.ctip.application.port.SubscriptionRepository;
import com.ctip.application.port.TenantMembershipRepository;
import com.ctip.application.port.WebhookRepository;
import com.ctip.application.rbac.RoleCode;
import com.ctip.domain.notification.HmacSecret;
import com.ctip.domain.notification.NotificationType;
import com.ctip.domain.notification.Webhook;
import com.ctip.domain.notification.WebhookFilter;
import com.ctip.domain.notification.WebhookSignature;
import com.ctip.domain.notification.WebhookStatus;
import com.ctip.domain.plan.PlanCode;
import com.ctip.domain.tenant.TenantId;
import com.ctip.sdk.Severity;
import com.ctip.support.RecordingWebhookSender;
import com.ctip.support.TestIdentities;
import com.ctip.support.TestPlans;
import com.ctip.support.WebhookFixtures;
import com.ctip.support.WebhookTestConfig;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * DoD M3-06 與 M3-07:webhook 送達、HMAC 簽章(含 timestamp 防重放)、失敗重試與連續五次後停用
 * (docs/spec/13-platform-ops.md §13.2;02 §2.3 的 W1–W4)。
 *
 * <p>簽章以<strong>建立時回傳的原文密鑰</strong>重新計算後比對:這同時驗證了 ADR 0021 的定調
 * ——密鑰以 AES-GCM 可還原地儲存,只存 SHA-256 的話伺服器根本算不出這個值。
 *
 * <p>不變量 W1(必須 https)與 W6(方案的數量上限)在 REST 層驗:
 * {@code NotificationApiTest} 分別斷言它們回 400 與 403,那才是使用者看得到的行為。
 */
@Import(WebhookTestConfig.class)
class WebhookDeliveryTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private WebhookManagementService webhooks;

    @Autowired
    private WebhookRepository webhookRepository;

    @Autowired
    private NotificationService notifications;

    @Autowired
    private WebhookDeliveryService deliveries;

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
    void deliversWithTheFiveHeadersAndASignatureOverTimestampAndBody() {
        WebhookFixtures.Owner tenant = premiumTenant("delivery-headers");
        WebhookManagementService.IssuedWebhook issued = WebhookFixtures.register(
                webhooks, tenant, "headers", Set.of(NotificationType.NEW_IOC), WebhookFilter.unfiltered());

        UUID eventId = idGenerator.nextId();
        notifications.dispatch(WebhookFixtures.newIoc(eventId, tenant.tenantId(), Severity.HIGH, Set.of(), Set.of()));

        List<WebhookRequest> sent = requestsFor(issued);
        assertThat(sent).hasSize(1);
        WebhookRequest request = sent.get(0);
        assertThat(request.eventId()).isEqualTo(eventId);
        assertThat(request.eventType()).isEqualTo(NotificationType.NEW_IOC);
        assertThat(request.attempt()).isEqualTo(1);
        assertThat(request.targetUrl()).startsWith("https://");

        // 簽章對象是 timestamp + "." + body(§13.2 定調;只有它防得了重放),不是原始 body
        String expected = WebhookSignature.header(
                new HmacSecret(issued.secret()).hex(WebhookSignature.payload(request.timestamp(), request.body())));
        assertThat(request.signature()).isEqualTo(expected);

        // 反面:少了 timestamp 前綴就對不上——證明 timestamp 真的進了簽章
        String withoutTimestamp = WebhookSignature.header(new HmacSecret(issued.secret()).hex(request.body()));
        assertThat(request.signature()).isNotEqualTo(withoutTimestamp);

        // timestamp 必須是真實時間,接收端才能用 5 分鐘的偏差窗判斷(§13.2)
        assertThat(WebhookSignature.withinClockSkew(Instant.ofEpochSecond(request.timestamp()), clock.now()))
                .isTrue();
    }

    @Test
    void aSuccessfulDeliveryIsRecordedAndResetsTheFailureCount() {
        WebhookFixtures.Owner tenant = premiumTenant("delivery-success");
        WebhookManagementService.IssuedWebhook issued = WebhookFixtures.register(
                webhooks, tenant, "success", Set.of(NotificationType.NEW_IOC), WebhookFilter.unfiltered());

        UUID eventId = idGenerator.nextId();
        notifications.dispatch(WebhookFixtures.newIoc(eventId, tenant.tenantId(), Severity.LOW, Set.of(), Set.of()));

        assertThat(deliveryStatuses(issued, eventId)).containsExactly("SUCCESS");
        Webhook stored = webhookRepository.findById(issued.webhook().id()).orElseThrow();
        assertThat(stored.status()).isEqualTo(WebhookStatus.ACTIVE);
        assertThat(stored.consecutiveFailures()).isZero();
        assertThat(stored.lastSuccessAt()).isNotNull();
    }

    @Test
    void aFailedDeliveryIsRetriedUpToFiveAttemptsThenAbandoned() {
        WebhookFixtures.Owner tenant = premiumTenant("delivery-retry");
        WebhookManagementService.IssuedWebhook issued = WebhookFixtures.register(
                webhooks, tenant, "retry", Set.of(NotificationType.NEW_IOC), WebhookFilter.unfiltered());
        sender.failAlways();

        UUID eventId = idGenerator.nextId();
        notifications.dispatch(WebhookFixtures.newIoc(eventId, tenant.tenantId(), Severity.LOW, Set.of(), Set.of()));
        drainRetries();

        assertThat(requestsFor(issued)).hasSize(Webhook.MAX_ATTEMPTS);
        assertThat(requestsFor(issued).stream().map(WebhookRequest::attempt)).containsExactly(1, 2, 3, 4, 5);
        assertThat(deliveryStatuses(issued, eventId))
                .containsExactly("FAILED", "FAILED", "FAILED", "FAILED", "ABANDONED");
        // 用盡之後不再排下一次重試
        assertThat(dueRetryCount(issued)).isZero();
    }

    @Test
    void fiveConsecutiveAbandonedEventsDisableTheWebhookAndRaiseWebhookDisabled() {
        WebhookFixtures.Owner tenant = premiumTenant("delivery-disable");
        WebhookManagementService.IssuedWebhook issued = WebhookFixtures.register(
                webhooks, tenant, "disable", Set.of(NotificationType.NEW_IOC), WebhookFilter.unfiltered());
        sender.failAlways();

        for (int event = 0; event < Webhook.FAILURE_THRESHOLD; event++) {
            notifications.dispatch(
                    WebhookFixtures.newIoc(idGenerator.nextId(), tenant.tenantId(), Severity.LOW, Set.of(), Set.of()));
            drainRetries();
        }

        Webhook disabled = webhookRepository.findById(issued.webhook().id()).orElseThrow();
        assertThat(disabled.status()).isEqualTo(WebhookStatus.DISABLED);
        assertThat(disabled.consecutiveFailures()).isGreaterThanOrEqualTo(Webhook.FAILURE_THRESHOLD);

        // W3:停用時發出 WebhookDisabled,而它是 SYSTEM_ALERT 型別的通知(§2.4)
        Integer alerts = jdbc.queryForObject(
                "select count(*) from notifications where tenant_id = ? and event_type = 'SYSTEM_ALERT'"
                        + " and resource_type = 'webhook' and resource_id = ?",
                Integer.class,
                tenant.tenantId().value(),
                issued.webhook().id().value());
        assertThat(alerts).isPositive();

        // 停用之後不再送達任何事件(matches() 只認 ACTIVE)
        int before = requestsFor(issued).size();
        notifications.dispatch(
                WebhookFixtures.newIoc(idGenerator.nextId(), tenant.tenantId(), Severity.LOW, Set.of(), Set.of()));
        assertThat(requestsFor(issued)).hasSize(before);
    }

    @Test
    void aRecoveredDeliveryClearsTheFailureCountBeforeTheThresholdIsReached() {
        WebhookFixtures.Owner tenant = premiumTenant("delivery-recover");
        WebhookManagementService.IssuedWebhook issued = WebhookFixtures.register(
                webhooks, tenant, "recover", Set.of(NotificationType.NEW_IOC), WebhookFilter.unfiltered());

        sender.failAlways();
        for (int event = 0; event < Webhook.FAILURE_THRESHOLD - 1; event++) {
            notifications.dispatch(
                    WebhookFixtures.newIoc(idGenerator.nextId(), tenant.tenantId(), Severity.LOW, Set.of(), Set.of()));
            drainRetries();
        }
        assertThat(webhookRepository
                        .findById(issued.webhook().id())
                        .orElseThrow()
                        .status())
                .isEqualTo(WebhookStatus.ACTIVE);

        sender.succeedAlways();
        notifications.dispatch(
                WebhookFixtures.newIoc(idGenerator.nextId(), tenant.tenantId(), Severity.LOW, Set.of(), Set.of()));

        Webhook recovered = webhookRepository.findById(issued.webhook().id()).orElseThrow();
        assertThat(recovered.status()).isEqualTo(WebhookStatus.ACTIVE);
        assertThat(recovered.consecutiveFailures()).isZero();
    }

    /**
     * 讓所有已排定的重試立即到期並執行,直到沒有東西可重試。
     *
     * <p>退避是 1/2/4/8 分鐘的真實時間(W4),測試不可能等;把 {@code next_retry_at} 改成過去
     * 等同於「時間到了」,重試路徑本身完全沒有被繞過。
     */
    private void drainRetries() {
        for (int round = 0; round < Webhook.MAX_ATTEMPTS; round++) {
            int updated = jdbc.update("update webhook_deliveries set next_retry_at = now() - interval '1 second'"
                    + " where status = 'FAILED' and next_retry_at is not null");
            if (updated == 0) {
                return;
            }
            deliveries.retryDue(100);
        }
    }

    private List<WebhookRequest> requestsFor(WebhookManagementService.IssuedWebhook issued) {
        String url = issued.webhook().targetUrl();
        return sender.requests().stream()
                .filter(request -> request.targetUrl().equals(url))
                .toList();
    }

    private List<String> deliveryStatuses(WebhookManagementService.IssuedWebhook issued, UUID eventId) {
        return jdbc.queryForList(
                "select status from webhook_deliveries where webhook_id = ? and event_id = ? order by attempt",
                String.class,
                issued.webhook().id().value(),
                eventId);
    }

    private int dueRetryCount(WebhookManagementService.IssuedWebhook issued) {
        Integer count = jdbc.queryForObject(
                "select count(*) from webhook_deliveries where webhook_id = ? and next_retry_at is not null",
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
