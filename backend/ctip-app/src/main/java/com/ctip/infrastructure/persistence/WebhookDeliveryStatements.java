package com.ctip.infrastructure.persistence;

import com.ctip.application.notification.DeliveryOutcome;
import com.ctip.application.notification.WebhookDeliveryAttempt;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * {@code webhook_deliveries} 的兩個 native 述句(理由同 {@link NotificationStatements}:
 * 參數個數超過 §1.8 規則 3 的上限)。
 */
@Component
class WebhookDeliveryStatements {

    /**
     * 佔位一次嘗試。{@code ON CONFLICT DO NOTHING}:{@code ux_wd_idempotent} 就是
     * §13.1 規則 5 的去重表,撞上它代表這次已經送過,<strong>不得</strong>當成錯誤往上丟
     * ——用 JPA 的 persist 再接 {@code DataIntegrityViolationException} 會把當前交易
     * 標成 rollback-only,連帶讓後續的寫入一起失敗。
     */
    private static final String INSERT_ATTEMPT = """
            INSERT INTO webhook_deliveries (id, webhook_id, event_id, event_type, attempt, status, created_at)
            VALUES (:id, :webhookId, :eventId, :eventType, :attempt, 'PENDING', :createdAt)
            ON CONFLICT (webhook_id, event_id, attempt) DO NOTHING
            """;

    private static final String COMPLETE = """
            UPDATE webhook_deliveries
               SET status = :status, http_status = :httpStatus, response_time_ms = :responseTimeMs,
                   error_message = :errorMessage, next_retry_at = :nextRetryAt, delivered_at = :deliveredAt
             WHERE id = :id
            """;

    /** 送達記錄是 append-only 且會被人讀,錯誤訊息只留可診斷的長度。 */
    private static final int MAX_ERROR_MESSAGE = 512;

    @PersistenceContext
    private EntityManager entityManager;

    /** @return false 表示這一次嘗試已經存在(事件重送或並行掃描);此次不得再送 */
    boolean insertAttempt(WebhookDeliveryAttempt attempt) {
        return entityManager
                        .createNativeQuery(INSERT_ATTEMPT)
                        .setParameter("id", attempt.id())
                        .setParameter("webhookId", attempt.webhookId().value())
                        .setParameter("eventId", attempt.eventId())
                        .setParameter("eventType", attempt.eventType().name())
                        .setParameter("attempt", (short) attempt.attempt())
                        .setParameter("createdAt", attempt.createdAt())
                        .executeUpdate()
                > 0;
    }

    void complete(UUID attemptId, DeliveryOutcome outcome) {
        entityManager
                .createNativeQuery(COMPLETE)
                .setParameter("id", attemptId)
                .setParameter("status", outcome.status().name())
                .setParameter(
                        "httpStatus",
                        outcome.httpStatus() == null
                                ? null
                                : outcome.httpStatus().shortValue())
                .setParameter("responseTimeMs", outcome.responseTimeMs())
                .setParameter("errorMessage", truncate(outcome.errorMessage()))
                .setParameter("nextRetryAt", outcome.nextRetryAt())
                .setParameter("deliveredAt", outcome.deliveredAt())
                .executeUpdate();
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= MAX_ERROR_MESSAGE ? message : message.substring(0, MAX_ERROR_MESSAGE);
    }
}
