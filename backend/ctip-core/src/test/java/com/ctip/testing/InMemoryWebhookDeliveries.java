package com.ctip.testing;

import com.ctip.application.notification.DeliveryOutcome;
import com.ctip.application.notification.WebhookDeliveryAttempt;
import com.ctip.application.port.WebhookDeliveryPort;
import com.ctip.domain.notification.DeliveryStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 測試用 in-memory WebhookDeliveryPort。
 * {@code ux_wd_idempotent} 的 {@code (webhookId, eventId, attempt)} 去重在此以清單掃描模擬。
 */
public final class InMemoryWebhookDeliveries implements WebhookDeliveryPort {

    private final List<WebhookDeliveryAttempt> attempts = new ArrayList<>();

    @Override
    public boolean beginAttempt(WebhookDeliveryAttempt attempt) {
        boolean exists = attempts.stream()
                .anyMatch(existing -> existing.webhookId().equals(attempt.webhookId())
                        && existing.eventId().equals(attempt.eventId())
                        && existing.attempt() == attempt.attempt());
        if (exists) {
            return false;
        }
        attempts.add(attempt);
        return true;
    }

    @Override
    public void complete(UUID attemptId, DeliveryOutcome outcome) {
        replace(
                attemptId,
                current -> new WebhookDeliveryAttempt(
                        current.id(),
                        current.webhookId(),
                        current.eventId(),
                        current.eventType(),
                        current.attempt(),
                        outcome.status(),
                        outcome.httpStatus(),
                        outcome.responseTimeMs(),
                        outcome.errorMessage(),
                        outcome.nextRetryAt(),
                        outcome.deliveredAt(),
                        current.createdAt()));
    }

    @Override
    public List<WebhookDeliveryAttempt> dueForRetry(Instant now, int limit) {
        return attempts.stream()
                .filter(attempt -> attempt.status() == DeliveryStatus.FAILED)
                .filter(attempt ->
                        attempt.nextRetryAt() != null && !attempt.nextRetryAt().isAfter(now))
                .limit(limit)
                .toList();
    }

    /**
     * 讓所有待重試的列立即到期。退避是 1/2/4/8 分鐘的真實時間(不變量 W4),
     * 單元測試不可能等;把時點推到過去等同於「時間到了」,重試路徑本身沒有被繞過。
     */
    public void makeAllRetriesDue(Instant due) {
        for (int i = 0; i < attempts.size(); i++) {
            WebhookDeliveryAttempt current = attempts.get(i);
            if (current.status() == DeliveryStatus.FAILED && current.nextRetryAt() != null) {
                attempts.set(i, withNextRetryAt(current, due));
            }
        }
    }

    private static WebhookDeliveryAttempt withNextRetryAt(WebhookDeliveryAttempt current, Instant due) {
        return new WebhookDeliveryAttempt(
                current.id(),
                current.webhookId(),
                current.eventId(),
                current.eventType(),
                current.attempt(),
                current.status(),
                current.httpStatus(),
                current.responseTimeMs(),
                current.errorMessage(),
                due,
                current.deliveredAt(),
                current.createdAt());
    }

    @Override
    public void clearRetrySchedule(UUID attemptId) {
        replace(
                attemptId,
                current -> new WebhookDeliveryAttempt(
                        current.id(),
                        current.webhookId(),
                        current.eventId(),
                        current.eventType(),
                        current.attempt(),
                        current.status(),
                        current.httpStatus(),
                        current.responseTimeMs(),
                        current.errorMessage(),
                        null,
                        current.deliveredAt(),
                        current.createdAt()));
    }

    public List<WebhookDeliveryAttempt> all() {
        return List.copyOf(attempts);
    }

    private void replace(UUID attemptId, java.util.function.UnaryOperator<WebhookDeliveryAttempt> change) {
        for (int i = 0; i < attempts.size(); i++) {
            if (attempts.get(i).id().equals(attemptId)) {
                attempts.set(i, change.apply(attempts.get(i)));
                return;
            }
        }
    }
}
