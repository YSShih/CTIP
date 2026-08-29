package com.ctip.application.notification;

import com.ctip.application.port.ClockPort;
import com.ctip.application.port.IdGeneratorPort;
import com.ctip.application.port.WebhookPayloadPort;
import com.ctip.application.port.WebhookSenderPort;
import com.ctip.domain.notification.DeliveryStatus;
import com.ctip.domain.notification.NotificationEvent;
import com.ctip.domain.notification.Webhook;
import com.ctip.domain.notification.WebhookRetryPolicy;
import com.ctip.domain.notification.WebhookSignature;
import com.ctip.domain.notification.WebhookStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Webhook 送達(docs/spec/13-platform-ops.md §13.2、02 §2.3 的 W3–W5)。
 *
 * <p>三件事在這裡強制:
 * <ul>
 *   <li><strong>W5 伺服器端過濾</strong>:候選集合取回後逐一 {@link Webhook#matches},
 *       不符者連一列 {@code webhook_deliveries} 都不會產生</li>
 *   <li><strong>§13.1 規則 5 冪等</strong>:每次嘗試先寫入
 *       {@code ux_wd_idempotent (webhook_id, event_id, attempt)};寫不進去就代表這次送過了</li>
 *   <li><strong>W3/W4</strong>:退避與停用由 {@link WebhookRetryPolicy} 與
 *       {@link Webhook#recordDelivery} 決定,本類別只負責搬運</li>
 * </ul>
 *
 * <p>交易邊界全部在 {@link NotificationTransactions}(理由見該類別);本類別自身無交易,
 * HTTP 送出因此不會被包在任何交易內。
 */
@Service
public class WebhookDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryService.class);

    private final NotificationTransactions transactions;
    private final WebhookSenderPort sender;
    private final WebhookPayloadPort payloads;
    private final IdGeneratorPort idGenerator;
    private final ClockPort clock;

    public WebhookDeliveryService(
            NotificationTransactions transactions,
            WebhookSenderPort sender,
            WebhookPayloadPort payloads,
            IdGeneratorPort idGenerator,
            ClockPort clock) {
        this.transactions = transactions;
        this.sender = sender;
        this.payloads = payloads;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    /** 一個事件的首次扇出。不符過濾條件的 webhook 完全不參與(不變量 W5)。 */
    public void fanOut(NotificationEvent event, NotificationRecord notification) {
        for (Webhook webhook : transactions.activeWebhooks()) {
            if (!webhook.matches(event)) {
                continue;
            }
            deliver(webhook, notification, 1);
        }
    }

    /**
     * 到期的重試(每 5 分鐘的 {@code NOTIFICATION_RETRY_CRON};08 §8.7)。
     *
     * @return 實際處理的筆數
     */
    public int retryDue(int limit) {
        List<WebhookDeliveryAttempt> due = transactions.dueForRetry(clock.now(), limit);
        for (WebhookDeliveryAttempt attempt : due) {
            try {
                retry(attempt);
            } catch (RuntimeException e) {
                log.warn(
                        "webhook 重試失敗:delivery={} webhook={}",
                        attempt.id(),
                        attempt.webhookId().value(),
                        e);
            } finally {
                // 無論結果如何都要把這一列移出重試佇列,否則它每 5 分鐘被撿一次
                transactions.clearRetrySchedule(attempt.id());
            }
        }
        return due.size();
    }

    private void retry(WebhookDeliveryAttempt attempt) {
        Optional<Webhook> webhook = transactions.findWebhook(attempt.webhookId());
        if (webhook.isEmpty() || webhook.get().status() != WebhookStatus.ACTIVE) {
            return;
        }
        Optional<NotificationRecord> notification = transactions.findNotification(attempt.eventId());
        if (notification.isEmpty()) {
            log.warn("找不到 eventId={} 的通知內容,放棄重試", attempt.eventId());
            return;
        }
        deliver(webhook.get(), notification.get(), attempt.attempt() + 1);
    }

    /**
     * 送出第 {@code attempt} 次。<strong>先佔位再送</strong>:
     * {@code beginAttempt} 寫不進去代表這個 {@code (webhook, eventId, attempt)} 已經送過
     * ——事件重送(§13.1 規則 5)或排程與扇出撞在一起,兩種都不得再送一次。
     */
    private void deliver(Webhook webhook, NotificationRecord notification, int attempt) {
        if (attempt > Webhook.MAX_ATTEMPTS) {
            return;
        }
        UUID attemptId = idGenerator.nextId();
        Instant startedAt = clock.now();
        boolean claimed = transactions.beginAttempt(new WebhookDeliveryAttempt(
                attemptId,
                webhook.id(),
                notification.eventId(),
                notification.eventType(),
                attempt,
                DeliveryStatus.PENDING,
                null,
                null,
                null,
                null,
                null,
                startedAt));
        if (!claimed) {
            return;
        }

        WebhookSendResult result = send(webhook, sign(webhook, notification, attempt, startedAt));
        Instant finishedAt = clock.now();
        DeliveryOutcome outcome = outcomeOf(result, attempt, finishedAt);

        transactions.completeAttempt(attemptId, outcome);
        transactions.applyOutcome(webhook.id(), outcome.status(), finishedAt);
    }

    /** 簽章對象為 {@code timestamp + "." + body}(§13.2 定調;只有它防得了重放)。 */
    private WebhookRequest sign(Webhook webhook, NotificationRecord notification, int attempt, Instant startedAt) {
        byte[] body = payloads.body(notification);
        long timestamp = startedAt.getEpochSecond();
        return new WebhookRequest(
                webhook.targetUrl(),
                WebhookSignature.header(webhook.sign(WebhookSignature.payload(timestamp, body))),
                notification.eventId(),
                notification.eventType(),
                attempt,
                timestamp,
                body);
    }

    private static DeliveryOutcome outcomeOf(WebhookSendResult result, int attempt, Instant finishedAt) {
        if (result.success()) {
            return DeliveryOutcome.succeeded(result, finishedAt);
        }
        return WebhookRetryPolicy.nextRetryAt(attempt, finishedAt)
                .map(nextRetryAt -> DeliveryOutcome.retryable(result, nextRetryAt))
                .orElseGet(() -> DeliveryOutcome.abandoned(result));
    }

    /** 送出端的任何例外都當成一次失敗,不得往上冒——上游是已提交的業務操作。 */
    private WebhookSendResult send(Webhook webhook, WebhookRequest request) {
        try {
            return sender.send(request);
        } catch (RuntimeException e) {
            log.warn("webhook 送出失敗:webhook={} event={}", webhook.id().value(), request.eventId(), e);
            return WebhookSendResult.failed(0, e.getClass().getSimpleName());
        }
    }
}
