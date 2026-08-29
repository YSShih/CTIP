package com.ctip.application.port;

import com.ctip.application.notification.DeliveryOutcome;
import com.ctip.application.notification.WebhookDeliveryAttempt;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** {@code webhook_deliveries} 的 port(append-only,兩模型表)。 */
public interface WebhookDeliveryPort {

    /**
     * 建立一列 {@code PENDING} 的送達嘗試。
     *
     * @return false 表示 {@code ux_wd_idempotent} 已有同一個 {@code (webhookId, eventId, attempt)}
     *     ——事件重送或多實例競爭,此次不得再送(§13.1 規則 5)
     */
    boolean beginAttempt(WebhookDeliveryAttempt attempt);

    /** 以送達結果收尾。 */
    void complete(UUID attemptId, DeliveryOutcome outcome);

    /** 到期可重試的嘗試({@code status = FAILED} 且 {@code next_retry_at <= now};走 {@code ix_wd_retry})。 */
    List<WebhookDeliveryAttempt> dueForRetry(Instant now, int limit);

    /**
     * 把某一列移出重試佇列({@code next_retry_at = null})。
     *
     * <p>重試會另外寫一列(attempt + 1),舊列若留著 {@code next_retry_at} 就會每 5 分鐘被再撿一次。
     * append-only 指的是「不刪列、不改已定案的結果」,重試排程欄位本來就是該列生命週期的一部分
     * ——{@link #complete} 也在寫同一列。
     */
    void clearRetrySchedule(UUID attemptId);
}
