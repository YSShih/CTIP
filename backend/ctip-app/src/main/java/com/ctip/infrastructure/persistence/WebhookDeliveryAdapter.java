package com.ctip.infrastructure.persistence;

import com.ctip.application.notification.DeliveryOutcome;
import com.ctip.application.notification.WebhookDeliveryAttempt;
import com.ctip.application.port.WebhookDeliveryPort;
import com.ctip.domain.notification.DeliveryStatus;
import com.ctip.domain.notification.NotificationType;
import com.ctip.domain.notification.WebhookId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * WebhookDeliveryPort 的 JPA 實作(表 25,append-only 兩模型)。
 * 交易邊界由呼叫端({@code NotificationTransactions})以 {@code REQUIRES_NEW} 決定。
 */
@Repository
@Transactional
class WebhookDeliveryAdapter implements WebhookDeliveryPort {

    private final WebhookDeliveryJpaRepository jpa;
    private final WebhookDeliveryStatements statements;

    WebhookDeliveryAdapter(WebhookDeliveryJpaRepository jpa, WebhookDeliveryStatements statements) {
        this.jpa = jpa;
        this.statements = statements;
    }

    @Override
    public boolean beginAttempt(WebhookDeliveryAttempt attempt) {
        return statements.insertAttempt(attempt);
    }

    @Override
    public void complete(UUID attemptId, DeliveryOutcome outcome) {
        statements.complete(attemptId, outcome);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WebhookDeliveryAttempt> dueForRetry(Instant now, int limit) {
        return jpa.dueForRetry(now, limit).stream()
                .map(WebhookDeliveryAdapter::toAttempt)
                .toList();
    }

    @Override
    public void clearRetrySchedule(UUID attemptId) {
        jpa.clearRetrySchedule(attemptId);
    }

    private static WebhookDeliveryAttempt toAttempt(WebhookDeliveryEntity e) {
        return new WebhookDeliveryAttempt(
                e.id,
                new WebhookId(e.webhookId),
                e.eventId,
                NotificationType.valueOf(e.eventType),
                e.attempt,
                DeliveryStatus.valueOf(e.status),
                e.httpStatus == null ? null : (int) e.httpStatus,
                e.responseTimeMs,
                e.errorMessage,
                e.nextRetryAt,
                e.deliveredAt,
                e.createdAt);
    }
}
