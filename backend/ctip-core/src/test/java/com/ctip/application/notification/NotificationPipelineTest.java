package com.ctip.application.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ctip.application.plan.PlanLimitExceededException;
import com.ctip.application.plan.QuotaService;
import com.ctip.application.port.ClockPort;
import com.ctip.domain.notification.DeliveryStatus;
import com.ctip.domain.notification.NotificationEvent;
import com.ctip.domain.notification.NotificationType;
import com.ctip.domain.notification.Webhook;
import com.ctip.domain.notification.WebhookFilter;
import com.ctip.domain.notification.WebhookSignature;
import com.ctip.domain.notification.WebhookStatus;
import com.ctip.domain.plan.PlanCode;
import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.user.UserId;
import com.ctip.sdk.IocType;
import com.ctip.sdk.Severity;
import com.ctip.testing.CountingRateLimiter;
import com.ctip.testing.FixedClockPort;
import com.ctip.testing.InMemoryNotifications;
import com.ctip.testing.InMemoryPlanRepository;
import com.ctip.testing.InMemorySubscriptionRepository;
import com.ctip.testing.InMemoryWebhookDeliveries;
import com.ctip.testing.InMemoryWebhookRepository;
import com.ctip.testing.PlanFixtures;
import com.ctip.testing.RecordingEventPublisher;
import com.ctip.testing.RecordingWebhookDispatch;
import com.ctip.testing.SequentialIdGenerator;
import com.ctip.testing.SequentialTokenGenerator;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * 通知管線的編排:落庫 → 推播 → webhook 扇出,以及 W3–W6
 * (docs/spec/13-platform-ops.md §13.1、§13.2)。
 *
 * <p>SQL 面的冪等(兩個唯一索引)由 {@code EventIdempotencyTest} 對真資料庫驗;
 * 這裡驗的是<strong>編排</strong>:誰在什麼條件下被呼叫、呼叫幾次。
 */
@Tag("unit")
class NotificationPipelineTest {

    private static final Instant NOW = Instant.parse("2026-08-29T10:00:00Z");
    private static final TenantId TENANT = new TenantId(new UUID(0, 7));
    private static final UserId USER = new UserId(new UUID(0, 8));

    private final InMemoryWebhookRepository webhooks = new InMemoryWebhookRepository();
    private final InMemoryWebhookDeliveries deliveries = new InMemoryWebhookDeliveries();
    private final InMemoryNotifications notifications = new InMemoryNotifications();
    private final RecordingWebhookDispatch dispatch = new RecordingWebhookDispatch();
    private final RecordingEventPublisher events = new RecordingEventPublisher();
    private final ClockPort clock = new FixedClockPort(NOW);
    private final SequentialIdGenerator ids = new SequentialIdGenerator();

    private final NotificationTransactions transactions =
            new NotificationTransactions(webhooks, deliveries, notifications, events);
    private final WebhookDeliveryService delivery =
            new WebhookDeliveryService(transactions, dispatch, dispatch, ids, clock);
    private final NotificationService service = new NotificationService(
            notifications, transactions, dispatch, delivery, new NotificationService.Ports(ids, clock));
    private final WebhookManagementService management = management();

    @Test
    void dispatchStoresPushesAndFansOut() {
        register("all", WebhookFilter.unfiltered());
        service.dispatch(event(ids.nextId(), Severity.HIGH));

        assertThat(notifications.all()).hasSize(1);
        assertThat(dispatch.pushed()).hasSize(1);
        assertThat(dispatch.requests()).hasSize(1);
        assertThat(deliveries.all()).hasSize(1);
        assertThat(deliveries.all().get(0).status()).isEqualTo(DeliveryStatus.SUCCESS);
    }

    /** 重送:通知列與送達列都只有一份,而且不會再推播一次(否則 UI 會跳兩次)。 */
    @Test
    void aResentEventProducesNoDuplicateSideEffect() {
        register("all", WebhookFilter.unfiltered());
        NotificationEvent event = event(ids.nextId(), Severity.HIGH);

        service.dispatch(event);
        service.dispatch(event);

        assertThat(notifications.all()).hasSize(1);
        assertThat(dispatch.pushed()).hasSize(1);
        assertThat(dispatch.requests()).hasSize(1);
    }

    /** 不變量 W5:不符過濾條件的 webhook 連一列送達記錄都不會有。 */
    @Test
    void aWebhookThatDoesNotMatchIsNotEvenRecorded() {
        register("critical-only", new WebhookFilter(Set.of(), Severity.CRITICAL, Set.of(), Set.of()));
        service.dispatch(event(ids.nextId(), Severity.LOW));

        assertThat(dispatch.requests()).isEmpty();
        assertThat(deliveries.all()).isEmpty();
        // 站內通知不受 webhook 過濾影響
        assertThat(notifications.all()).hasSize(1);
    }

    /** 簽章對象是 {@code timestamp + "." + body}(§13.2 定調)。 */
    @Test
    void theSignatureCoversTheTimestampAndBody() {
        WebhookManagementService.IssuedWebhook issued = register("signed", WebhookFilter.unfiltered());
        service.dispatch(event(ids.nextId(), Severity.HIGH));

        WebhookRequest request = dispatch.requests().get(0);
        String expected = WebhookSignature.header(new com.ctip.domain.notification.HmacSecret(issued.secret())
                .hex(WebhookSignature.payload(request.timestamp(), request.body())));
        assertThat(request.signature()).isEqualTo(expected);
        assertThat(request.timestamp()).isEqualTo(NOW.getEpochSecond());
    }

    /** 不變量 W4:失敗後排定重試,{@code retryDue} 把它推進到下一次嘗試。 */
    @Test
    void aFailedDeliveryIsScheduledAndThenRetried() {
        register("retry", WebhookFilter.unfiltered());
        dispatch.failAlways();

        service.dispatch(event(ids.nextId(), Severity.HIGH));
        assertThat(deliveries.all()).hasSize(1);
        assertThat(deliveries.all().get(0).status()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(deliveries.all().get(0).nextRetryAt()).isEqualTo(NOW.plusSeconds(60));

        // 還沒到期:掃描不得把它撿起來
        assertThat(delivery.retryDue(10)).isZero();

        deliveries.makeAllRetriesDue(NOW.minusSeconds(1));
        assertThat(delivery.retryDue(10)).isEqualTo(1);
        assertThat(deliveries.all()).hasSize(2);
        assertThat(deliveries.all().get(1).attempt()).isEqualTo(2);
    }

    /** 不變量 W3:連續五個事件用盡重試 → DISABLED,並發出 WebhookDisabled。 */
    @Test
    void fiveAbandonedEventsDisableTheWebhook() {
        WebhookManagementService.IssuedWebhook issued = register("dying", WebhookFilter.unfiltered());
        // 只嘗試一次就放棄:第五次嘗試沒有下一次重試,直接 ABANDONED
        dispatch.respondWith(attempt -> com.ctip.application.notification.WebhookSendResult.rejected(500, 1));

        for (int event = 0; event < Webhook.FAILURE_THRESHOLD; event++) {
            exhaust(event(ids.nextId(), Severity.HIGH));
        }

        assertThat(webhooks.findById(issued.webhook().id()).orElseThrow().status())
                .isEqualTo(WebhookStatus.DISABLED);
        assertThat(events.published())
                .anyMatch(published -> published instanceof com.ctip.domain.event.WebhookEvents.WebhookDisabled);
    }

    /** 不變量 W6:數量上限由方案決定;FREE 的 max_webhooks 是 0 → 403。 */
    @Test
    void theFreePlanCannotRegisterAWebhook() {
        QuotaService quotas = quotas(PlanCode.FREE);
        WebhookManagementService free =
                new WebhookManagementService(webhooks, quotas, new SequentialTokenGenerator(), ids, clock);
        assertThatThrownBy(() -> free.register(command("nope", WebhookFilter.unfiltered())))
                .isInstanceOf(PlanLimitExceededException.class);
    }

    @Test
    void listAndDeleteAreScopedToTheOwningTenant() {
        WebhookManagementService.IssuedWebhook issued = register("owned", WebhookFilter.unfiltered());
        TenantId stranger = new TenantId(new UUID(0, 99));

        assertThat(management.list(TENANT)).hasSize(1);
        assertThat(management.list(stranger)).isEmpty();
        assertThat(management.delete(issued.webhook().id(), stranger)).isFalse();
        assertThat(management.delete(issued.webhook().id(), TENANT)).isTrue();
        assertThat(management.list(TENANT)).isEmpty();
    }

    /** 把一個事件的五次嘗試全部跑完;退避時點被推到過去,重試路徑本身不變。 */
    private void exhaust(NotificationEvent event) {
        service.dispatch(event);
        for (int round = 0; round < Webhook.MAX_ATTEMPTS; round++) {
            deliveries.makeAllRetriesDue(NOW.minusSeconds(1));
            delivery.retryDue(10);
        }
    }

    private WebhookManagementService.IssuedWebhook register(String name, WebhookFilter filter) {
        return management.register(command(name, filter));
    }

    private NewWebhookCommand command(String name, WebhookFilter filter) {
        return new NewWebhookCommand(
                TENANT, USER, name, "https://hooks.example.invalid/" + name, Set.of(NotificationType.NEW_IOC), filter);
    }

    private WebhookManagementService management() {
        return new WebhookManagementService(
                webhooks, quotas(PlanCode.PREMIUM), new SequentialTokenGenerator(), ids, clock);
    }

    private QuotaService quotas(PlanCode code) {
        InMemoryPlanRepository plans = new InMemoryPlanRepository();
        for (PlanCode each : PlanCode.values()) {
            plans.save(PlanFixtures.of(each));
        }
        InMemorySubscriptionRepository subscriptions = new InMemorySubscriptionRepository();
        if (code != PlanCode.FREE) {
            subscriptions.save(com.ctip.domain.plan.Subscription.subscribe(
                    new com.ctip.domain.plan.SubscriptionId(ids.nextId()),
                    TENANT,
                    PlanFixtures.of(code),
                    com.ctip.domain.plan.SubscriptionProvider.MANUAL,
                    com.ctip.domain.plan.BillingPeriod.openEnded(NOW)));
        }
        return new QuotaService(plans, subscriptions, new CountingRateLimiter(clock), clock);
    }

    private static NotificationEvent event(UUID eventId, Severity severity) {
        return new NotificationEvent(
                eventId,
                NotificationType.NEW_IOC,
                TENANT,
                NOW,
                "trace",
                "新增 IOC",
                null,
                severity,
                "indicator",
                new UUID(0, 42),
                null,
                Set.of(IocType.IPV4),
                Set.of(),
                Set.of());
    }
}
