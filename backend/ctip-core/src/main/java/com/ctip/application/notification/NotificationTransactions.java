package com.ctip.application.notification;

import com.ctip.application.port.EventPublisherPort;
import com.ctip.application.port.NotificationPort;
import com.ctip.application.port.WebhookDeliveryPort;
import com.ctip.application.port.WebhookRepository;
import com.ctip.domain.notification.DeliveryStatus;
import com.ctip.domain.notification.Webhook;
import com.ctip.domain.notification.WebhookId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 通知管線(落庫 → 推播 → webhook 送達)的交易邊界集中處。
 *
 * <p><strong>為什麼要獨立成一個 bean</strong>:兩個限制同時成立——
 * <ol>
 *   <li>送達由 AFTER_COMMIT 的事件消費端觸發,那個回呼仍在已提交交易的 synchronization 範圍內。
 *       預設的 {@code REQUIRED} 會去參與一個已經結束的交易,寫入沒有自己的提交邊界
 *       ——02 §2.4 因此明訂 AFTER_COMMIT 的消費端一律 {@code REQUIRES_NEW}。</li>
 *   <li>HTTP 送達不得包在交易裡(一次送達可能耗上數秒,交易會抓著連線不放),
 *       所以「寫佔位列 → 送出 → 寫結果」必須是三段,不能是一段。</li>
 * </ol>
 * 而 Spring 的交易是 proxy:{@link WebhookDeliveryService} 或 {@link NotificationService}
 * 自呼叫自己的 {@code @Transactional} 方法完全不會經過 proxy,標了也沒有作用。
 * 獨立成一個 bean 是讓這些邊界真的存在的唯一方式。
 */
@Service
public class NotificationTransactions {

    private final WebhookRepository webhooks;
    private final WebhookDeliveryPort deliveries;
    private final NotificationPort notifications;
    private final EventPublisherPort events;

    public NotificationTransactions(
            WebhookRepository webhooks,
            WebhookDeliveryPort deliveries,
            NotificationPort notifications,
            EventPublisherPort events) {
        this.webhooks = webhooks;
        this.deliveries = deliveries;
        this.notifications = notifications;
        this.events = events;
    }

    /**
     * 通知列的冪等寫入。回傳「這次是否真的插入」與最終落庫的那一列
     * ——重送時要拿既有的那一列,送達 payload 是它的純函數。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Persisted persist(NotificationRecord candidate) {
        if (notifications.recordIfAbsent(candidate)) {
            return new Persisted(candidate, true);
        }
        return new Persisted(notifications.findByEventId(candidate.eventId()).orElse(candidate), false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<Webhook> activeWebhooks() {
        return webhooks.findAllActive();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<Webhook> findWebhook(WebhookId id) {
        return webhooks.findById(id);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<NotificationRecord> findNotification(UUID eventId) {
        return notifications.findByEventId(eventId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<WebhookDeliveryAttempt> dueForRetry(Instant now, int limit) {
        return deliveries.dueForRetry(now, limit);
    }

    /** @return false 表示 {@code ux_wd_idempotent} 已有這一次嘗試(事件重送);此次不得再送 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean beginAttempt(WebhookDeliveryAttempt attempt) {
        return deliveries.beginAttempt(attempt);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeAttempt(UUID attemptId, DeliveryOutcome outcome) {
        deliveries.complete(attemptId, outcome);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void clearRetrySchedule(UUID attemptId) {
        deliveries.clearRetrySchedule(attemptId);
    }

    /**
     * 把結果套回聚合(不變量 W3)。<strong>重新載入再套用</strong>:送達開始時取得的那一份
     * 可能已被同一批的其他事件改過 {@code consecutiveFailures},沿用舊的會少算。
     *
     * <p>聚合發出的 {@code WebhookDisabled} 在<strong>這個交易內</strong>發佈:
     * {@code EventPublisherPort} 會把它掛在當前交易的 AFTER_COMMIT 上,在交易外呼叫則會
     * 掛到一個已經走完 afterCommit 階段的交易上,那一份永遠不會被觸發。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void applyOutcome(WebhookId webhookId, DeliveryStatus outcome, Instant now) {
        webhooks.findById(webhookId).ifPresent(current -> {
            current.recordDelivery(outcome, now);
            webhooks.save(current);
            current.pullEvents().forEach(events::publish);
        });
    }

    /** 通知列 + 「這次是否真的插入」。 */
    public record Persisted(NotificationRecord notification, boolean inserted) {}
}
