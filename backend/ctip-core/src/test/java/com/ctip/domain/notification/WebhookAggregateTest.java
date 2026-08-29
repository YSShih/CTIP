package com.ctip.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ctip.domain.event.DomainEvent;
import com.ctip.domain.event.WebhookEvents;
import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.user.UserId;
import com.ctip.sdk.IocType;
import com.ctip.sdk.Severity;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Webhook 聚合的六條不變量 W1–W6(docs/spec/02-ddd-model.md §2.3;§14.2 要求逐條覆蓋)。 */
@Tag("unit")
class WebhookAggregateTest {

    private static final Instant NOW = Instant.parse("2026-08-29T08:00:00Z");
    private static final TenantId TENANT = new TenantId(new UUID(0, 7));
    private static final UserId USER = new UserId(new UUID(0, 8));
    private static final UUID SOURCE = new UUID(0, 9);

    /** W1:targetUrl 必須為 https://。 */
    @Test
    void plainHttpTargetsAreRejected() {
        assertThatThrownBy(() -> Webhook.register(snapshot("http://hooks.example.invalid/x", WebhookStatus.ACTIVE, 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("W1");
    }

    /** T3:public tenant 不得有 webhook。 */
    @Test
    void thePublicTenantCannotOwnAWebhook() {
        assertThatThrownBy(() -> Webhook.register(new WebhookSnapshot(
                        new WebhookId(new UUID(0, 1)),
                        TenantId.PUBLIC,
                        USER,
                        "public",
                        "https://hooks.example.invalid/x",
                        new HmacSecret("secret"),
                        Set.of(NotificationType.NEW_IOC),
                        WebhookFilter.unfiltered(),
                        WebhookStatus.ACTIVE,
                        0,
                        null,
                        null,
                        NOW)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 訂閱零個事件型別的 webhook 永遠不會送出任何東西——那不是有效狀態(執行規則 16)。 */
    @Test
    void aWebhookMustSubscribeToAtLeastOneEventType() {
        assertThatThrownBy(() -> Webhook.register(new WebhookSnapshot(
                        new WebhookId(new UUID(0, 1)),
                        TENANT,
                        USER,
                        "empty",
                        "https://hooks.example.invalid/x",
                        new HmacSecret("secret"),
                        Set.of(),
                        WebhookFilter.unfiltered(),
                        WebhookStatus.ACTIVE,
                        0,
                        null,
                        null,
                        NOW)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** W3:連續五個事件用盡重試 → DISABLED + WebhookDisabled。 */
    @Test
    void fiveAbandonedEventsDisableTheWebhookOnceAndOnlyOnce() {
        Webhook webhook = active();
        for (int i = 0; i < Webhook.FAILURE_THRESHOLD - 1; i++) {
            webhook.recordDelivery(DeliveryStatus.ABANDONED, NOW);
            assertThat(webhook.status()).isEqualTo(WebhookStatus.ACTIVE);
        }
        webhook.recordDelivery(DeliveryStatus.ABANDONED, NOW);

        assertThat(webhook.status()).isEqualTo(WebhookStatus.DISABLED);
        List<DomainEvent> events = webhook.pullEvents();
        assertThat(events).hasSize(1).first().isInstanceOf(WebhookEvents.WebhookDisabled.class);

        // 已停用之後再失敗不會再發第二次事件
        webhook.recordDelivery(DeliveryStatus.ABANDONED, NOW);
        assertThat(webhook.pullEvents()).isEmpty();
    }

    /**
     * {@code consecutiveFailures} 計的是「事件」不是「嘗試」:
     * 若計嘗試次數,一個 ABANDONED 事件(五次嘗試)就會立刻觸發 W3,W3 便完全等同於 W4。
     */
    @Test
    void aRetryableFailureDoesNotCountTowardsTheDisableThreshold() {
        Webhook webhook = active();
        for (int i = 0; i < 10; i++) {
            webhook.recordDelivery(DeliveryStatus.FAILED, NOW);
        }
        assertThat(webhook.status()).isEqualTo(WebhookStatus.ACTIVE);
        assertThat(webhook.consecutiveFailures()).isZero();
    }

    @Test
    void aSuccessResetsTheFailureCount() {
        Webhook webhook = active();
        webhook.recordDelivery(DeliveryStatus.ABANDONED, NOW);
        webhook.recordDelivery(DeliveryStatus.ABANDONED, NOW);
        webhook.recordDelivery(DeliveryStatus.SUCCESS, NOW);

        assertThat(webhook.consecutiveFailures()).isZero();
        assertThat(webhook.lastSuccessAt()).isEqualTo(NOW);
    }

    /** DISABLED 是終態:暫停/恢復不得把它救回來。 */
    @Test
    void aDisabledWebhookCannotBeResumed() {
        Webhook webhook = Webhook.reconstitute(
                snapshot("https://hooks.example.invalid/x", WebhookStatus.DISABLED, Webhook.FAILURE_THRESHOLD));
        webhook.resume();
        assertThat(webhook.status()).isEqualTo(WebhookStatus.DISABLED);
    }

    /** W5:只有 ACTIVE、有訂閱該型別、且通過過濾的事件才送。 */
    @Test
    void matchesRequiresStatusEventTypeAndFilter() {
        Webhook webhook = Webhook.register(new WebhookSnapshot(
                new WebhookId(new UUID(0, 1)),
                TENANT,
                USER,
                "filtered",
                "https://hooks.example.invalid/x",
                new HmacSecret("secret"),
                Set.of(NotificationType.NEW_IOC),
                new WebhookFilter(Set.of(IocType.IPV4), Severity.HIGH, Set.of(), Set.of(SOURCE)),
                WebhookStatus.ACTIVE,
                0,
                null,
                null,
                NOW));

        assertThat(webhook.matches(
                        event(NotificationType.NEW_IOC, Severity.HIGH, Set.of(IocType.IPV4), Set.of(SOURCE))))
                .isTrue();
        // 事件型別不符
        assertThat(webhook.matches(
                        event(NotificationType.IOC_REVOKED, Severity.HIGH, Set.of(IocType.IPV4), Set.of(SOURCE))))
                .isFalse();
        // 嚴重度不足
        assertThat(webhook.matches(
                        event(NotificationType.NEW_IOC, Severity.MEDIUM, Set.of(IocType.IPV4), Set.of(SOURCE))))
                .isFalse();
        // IOC 型別不符
        assertThat(webhook.matches(
                        event(NotificationType.NEW_IOC, Severity.HIGH, Set.of(IocType.DOMAIN), Set.of(SOURCE))))
                .isFalse();
        // 來源不符
        assertThat(webhook.matches(
                        event(NotificationType.NEW_IOC, Severity.HIGH, Set.of(IocType.IPV4), Set.of(new UUID(0, 99)))))
                .isFalse();

        webhook.suspend();
        assertThat(webhook.matches(
                        event(NotificationType.NEW_IOC, Severity.HIGH, Set.of(IocType.IPV4), Set.of(SOURCE))))
                .isFalse();
    }

    /** 租戶隔離:別的租戶的事件不得比對成功。 */
    @Test
    void eventsOfAnotherTenantNeverMatch() {
        Webhook webhook = active();
        NotificationEvent foreign = new NotificationEvent(
                new UUID(0, 20),
                NotificationType.NEW_IOC,
                new TenantId(new UUID(0, 77)),
                NOW,
                null,
                "別人的事件",
                null,
                Severity.HIGH,
                null,
                null,
                null,
                Set.of(),
                Set.of(),
                Set.of());
        assertThat(webhook.matches(foreign)).isFalse();
    }

    /** 平台範圍的事件(public tenant)對每個租戶都可見。 */
    @Test
    void platformWideEventsMatchEveryTenant() {
        Webhook webhook = active();
        NotificationEvent platform = new NotificationEvent(
                new UUID(0, 21),
                NotificationType.NEW_IOC,
                TenantId.PUBLIC,
                NOW,
                null,
                "平台事件",
                null,
                Severity.HIGH,
                null,
                null,
                null,
                Set.of(),
                Set.of(),
                Set.of());
        assertThat(webhook.matches(platform)).isTrue();
    }

    /** W4:最多五次嘗試,退避為 1/2/4/8 分鐘。 */
    @Test
    void theRetryScheduleIsExponentialAndBoundedAtFiveAttempts() {
        assertThat(WebhookRetryPolicy.nextRetryAt(1, NOW)).hasValue(NOW.plusSeconds(60));
        assertThat(WebhookRetryPolicy.nextRetryAt(2, NOW)).hasValue(NOW.plusSeconds(120));
        assertThat(WebhookRetryPolicy.nextRetryAt(3, NOW)).hasValue(NOW.plusSeconds(240));
        assertThat(WebhookRetryPolicy.nextRetryAt(4, NOW)).hasValue(NOW.plusSeconds(480));
        assertThat(WebhookRetryPolicy.nextRetryAt(Webhook.MAX_ATTEMPTS, NOW)).isEmpty();
        assertThatThrownBy(() -> WebhookRetryPolicy.nextRetryAt(0, NOW)).isInstanceOf(IllegalArgumentException.class);
    }

    /** W2 的可用面:聚合能以持有的原文密鑰簽章(只存雜湊時這個方法不可能存在)。 */
    @Test
    void signProducesTheHmacOfTheGivenPayload() {
        Webhook webhook = active();
        byte[] payload =
                WebhookSignature.payload(1755763200L, "{\"a\":1}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(webhook.sign(payload)).isEqualTo(new HmacSecret("secret").hex(payload));
        assertThat(webhook.sign(payload)).hasSize(64).matches("[0-9a-f]{64}");
    }

    /** 密鑰不得出現在 toString——它會經過日誌、例外訊息與 debugger。 */
    @Test
    void theSecretIsNeverPrinted() {
        assertThat(new HmacSecret("super-secret").toString()).doesNotContain("super-secret");
    }

    private static Webhook active() {
        return Webhook.register(snapshot("https://hooks.example.invalid/x", WebhookStatus.ACTIVE, 0));
    }

    private static WebhookSnapshot snapshot(String url, WebhookStatus status, int failures) {
        return new WebhookSnapshot(
                new WebhookId(new UUID(0, 1)),
                TENANT,
                USER,
                "hook",
                url,
                new HmacSecret("secret"),
                Set.of(NotificationType.NEW_IOC),
                WebhookFilter.unfiltered(),
                status,
                failures,
                null,
                null,
                NOW);
    }

    private static NotificationEvent event(
            NotificationType type, Severity severity, Set<IocType> iocTypes, Set<UUID> sourceIds) {
        return new NotificationEvent(
                new UUID(0, 30),
                type,
                TENANT,
                NOW,
                null,
                "事件",
                null,
                severity,
                "indicator",
                new UUID(0, 31),
                null,
                iocTypes,
                Set.of(),
                sourceIds);
    }
}
