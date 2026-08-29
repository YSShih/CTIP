package com.ctip.application.notification;

import com.ctip.domain.notification.DeliveryStatus;
import com.ctip.domain.notification.NotificationType;
import com.ctip.domain.notification.WebhookId;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code webhook_deliveries} 的一列(docs/spec/04-data-dictionary.md 表 25;append-only,兩模型)。
 *
 * <p>{@code (webhookId, eventId, attempt)} 是唯一鍵,同時是 §13.1 規則 5 的去重表:
 * 同一個 {@code eventId} 重送時第一次嘗試會直接撞上它,不會產生第二次送達。
 */
public record WebhookDeliveryAttempt(
        UUID id,
        WebhookId webhookId,
        UUID eventId,
        NotificationType eventType,
        int attempt,
        DeliveryStatus status,
        Integer httpStatus,
        Integer responseTimeMs,
        String errorMessage,
        Instant nextRetryAt,
        Instant deliveredAt,
        Instant createdAt) {}
