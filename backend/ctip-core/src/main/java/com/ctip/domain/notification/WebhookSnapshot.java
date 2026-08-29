package com.ctip.domain.notification;

import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.user.UserId;
import java.time.Instant;
import java.util.Set;

/**
 * Webhook 的持久化狀態(docs/spec/04-data-dictionary.md 表 24)。
 * 與其他聚合一致:建立與重建都經 snapshot,聚合本身不暴露 setter。
 */
public record WebhookSnapshot(
        WebhookId id,
        TenantId tenantId,
        UserId createdByUserId,
        String name,
        String targetUrl,
        HmacSecret secret,
        Set<NotificationType> eventTypes,
        WebhookFilter filter,
        WebhookStatus status,
        int consecutiveFailures,
        Instant lastDeliveryAt,
        Instant lastSuccessAt,
        Instant createdAt) {}
