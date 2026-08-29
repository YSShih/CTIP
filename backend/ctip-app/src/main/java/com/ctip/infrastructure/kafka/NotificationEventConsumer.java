package com.ctip.infrastructure.kafka;

import com.ctip.application.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;

/**
 * {@code ctip.notification.events.v1} 的消費端(§13.2「Kafka consumer(M3)」)。
 *
 * <p>冪等由 {@link NotificationService#dispatch} 承擔(§13.1 規則 5):去重鍵是
 * {@code eventId},落點是 {@code ux_notif_idempotent} 與 {@code ux_wd_idempotent} 兩個唯一索引
 * ——不是記憶體裡的一個 Set,重啟後仍然有效。
 *
 * <p>處理失敗只記錄不重拋:預設的容器錯誤處理會重試再送同一筆,而下游本來就是冪等的,
 * 但「一筆壞訊息卡住整個 partition」比漏一筆通知更糟。真正遺失的通知由站內通知列補償
 * ——它在 dispatch 的第一步就落庫。
 */
public class NotificationEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventConsumer.class);

    private final EventJsonCodec codec;
    private final NotificationService notifications;

    public NotificationEventConsumer(EventJsonCodec codec, NotificationService notifications) {
        this.codec = codec;
        this.notifications = notifications;
    }

    @KafkaListener(topics = KafkaTopics.NOTIFICATION_EVENTS, groupId = "ctip-notification")
    public void onNotificationEvent(String payload) {
        try {
            notifications.dispatch(codec.decodeNotification(payload));
        } catch (RuntimeException e) {
            log.warn("通知事件處理失敗,已略過該筆;站內通知不受影響", e);
        }
    }
}
