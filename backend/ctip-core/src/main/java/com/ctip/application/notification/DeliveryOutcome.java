package com.ctip.application.notification;

import com.ctip.domain.notification.DeliveryStatus;
import java.time.Instant;

/**
 * 一次送達嘗試的最終結果,寫回 {@code webhook_deliveries} 的那一列。
 *
 * <p>把六個欄位收成一個 record 而不是攤成參數:它們是同一件事的不同面向
 * (§1.8 的可讀性規則,參數上限 5 個),而且 {@code (status, nextRetryAt, deliveredAt)}
 * 三者之間有約束——只有 {@code FAILED} 有下次重試時點、只有 {@code SUCCESS} 有送達時間。
 */
public record DeliveryOutcome(
        DeliveryStatus status,
        Integer httpStatus,
        Integer responseTimeMs,
        String errorMessage,
        Instant nextRetryAt,
        Instant deliveredAt) {

    public static DeliveryOutcome succeeded(WebhookSendResult result, Instant deliveredAt) {
        return new DeliveryOutcome(
                DeliveryStatus.SUCCESS, result.httpStatus(), result.responseTimeMs(), null, null, deliveredAt);
    }

    public static DeliveryOutcome retryable(WebhookSendResult result, Instant nextRetryAt) {
        return new DeliveryOutcome(
                DeliveryStatus.FAILED,
                result.httpStatus(),
                result.responseTimeMs(),
                result.errorMessage(),
                nextRetryAt,
                null);
    }

    public static DeliveryOutcome abandoned(WebhookSendResult result) {
        return new DeliveryOutcome(
                DeliveryStatus.ABANDONED,
                result.httpStatus(),
                result.responseTimeMs(),
                result.errorMessage(),
                null,
                null);
    }
}
