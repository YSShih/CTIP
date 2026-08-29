package com.ctip.support;

import com.ctip.application.notification.NewWebhookCommand;
import com.ctip.application.notification.WebhookManagementService;
import com.ctip.domain.notification.NotificationEvent;
import com.ctip.domain.notification.NotificationType;
import com.ctip.domain.notification.WebhookFilter;
import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.user.UserId;
import com.ctip.sdk.Severity;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** webhook 與通知事件的測試建構子;三個 webhook 測試共用。 */
public final class WebhookFixtures {

    private WebhookFixtures() {}

    public static WebhookManagementService.IssuedWebhook register(
            WebhookManagementService webhooks,
            Owner owner,
            String name,
            Set<NotificationType> eventTypes,
            WebhookFilter filter) {
        return webhooks.register(new NewWebhookCommand(
                owner.tenantId(),
                owner.userId(),
                name,
                "https://hooks.ctip-sample.invalid/" + name,
                eventTypes,
                filter));
    }

    /** 建立 webhook 的租戶與使用者;三個測試共用同一個形狀。 */
    public record Owner(TenantId tenantId, UserId userId) {}

    /** 一個租戶自有的 {@code NEW_IOC} 通知事件。 */
    public static NotificationEvent newIoc(
            UUID eventId, TenantId tenantId, Severity severity, Set<String> tags, Set<UUID> sourceIds) {
        return new NotificationEvent(
                eventId,
                NotificationType.NEW_IOC,
                tenantId,
                Instant.parse("2026-08-29T10:00:00Z"),
                "trace-" + eventId,
                "新增 IOC:198.51.100.7",
                "型別 IPV4",
                severity,
                "indicator",
                UUID.nameUUIDFromBytes(("indicator-" + eventId).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                null,
                Set.of(com.ctip.sdk.IocType.IPV4),
                tags,
                sourceIds);
    }
}
