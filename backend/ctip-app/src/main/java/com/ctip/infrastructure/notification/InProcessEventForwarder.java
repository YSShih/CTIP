package com.ctip.infrastructure.notification;

import com.ctip.application.notification.NotificationEventFactory;
import com.ctip.application.notification.NotificationService;
import com.ctip.domain.notification.EventContext;
import com.ctip.infrastructure.event.DomainEventEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;

/**
 * {@code NOTIFICATION_TRANSPORT=in-process} 時的通知轉發(mvp / dev 沒有 broker)。
 *
 * <p>與 Kafka 路徑<strong>共用同一個入口</strong> {@link NotificationService#dispatch}:
 * 兩條路徑的副作用因此完全一致,冪等也只有一份實作。差別只在「事件從哪裡來」。
 *
 * <p>轉發是同步的:mvp/dev 是開發環境,同步讓測試可以直接斷言副作用;
 * staging/prod 走 Kafka,扇出本來就發生在 consumer 執行緒上。
 * 無論如何,任何例外都只記錄——上游是已提交的業務操作(§13.1 規則 7 的同一條理由)。
 */
public class InProcessEventForwarder {

    private static final Logger log = LoggerFactory.getLogger(InProcessEventForwarder.class);

    private final NotificationEventFactory factory;
    private final NotificationService notifications;

    public InProcessEventForwarder(NotificationEventFactory factory, NotificationService notifications) {
        this.factory = factory;
        this.notifications = notifications;
    }

    @EventListener
    public void onDomainEvent(DomainEventEnvelope envelope) {
        try {
            factory.from(contextOf(envelope), envelope.event()).ifPresent(notifications::dispatch);
        } catch (RuntimeException e) {
            log.warn("程序內通知轉發失敗,只記錄不影響已提交的業務操作;event={}", envelope.eventId(), e);
        }
    }

    private static EventContext contextOf(DomainEventEnvelope envelope) {
        return new EventContext(envelope.eventId(), envelope.occurredAt(), envelope.traceId());
    }
}
