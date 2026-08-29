package com.ctip.infrastructure.kafka;

import com.ctip.application.notification.NotificationEventFactory;
import com.ctip.domain.notification.EventContext;
import com.ctip.domain.notification.NotificationEvent;
import com.ctip.infrastructure.event.DomainEventEnvelope;
import jakarta.annotation.PreDestroy;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * §13.1 的 {@code KafkaForwardingListener}:把 domain event 轉發到 Kafka。
 * <strong>不修改任何發佈端</strong>——這裡只是 {@code DomainEventEnvelope} 的又一個消費端。
 *
 * <p><strong>為什麼是 {@code @EventListener} 而不是 {@code @TransactionalEventListener(AFTER_COMMIT)}</strong>:
 * {@code SpringEventPublisherAdapter} 已經<strong>在 AFTER_COMMIT 才發佈</strong>信封(Phase 6 起如此)。
 * 事件抵達時交易早已提交,再宣告一次 transactional phase 是多餘的
 * (與 {@code ThreatConsistencyListener} 同一個判斷,Phase 18 已定調)。
 *
 * <p><strong>§13.1 規則 7:Kafka 不可用時不得使業務操作失敗。</strong>兩層防護:
 * <ol>
 *   <li>轉發<strong>不在業務執行緒上進行</strong>。{@code KafkaTemplate.send()} 在取不到 metadata 時
 *       會<strong>同步阻塞</strong>到 {@code max.block.ms}(預設 60 秒)——broker 掛掉時,
 *       每一個事件都會讓剛提交完交易的那個請求多等一分鐘。回 200 但要等一分鐘,
 *       實務上與失敗沒有差別。</li>
 *   <li>佇列有界且滿了就丟棄(只記錄)。無界佇列在長時間斷線下會把堆積吃光,
 *       那才是真的讓業務操作失敗。事件遺失由站內通知列補償——它在程序內就已落庫。</li>
 * </ol>
 */
public class KafkaEventForwarder {

    /** 單一執行緒即可:轉發是 IO,而且同一個 topic 的順序性只在單執行緒下才有意義。 */
    private static final int QUEUE_CAPACITY = 10_000;

    private static final Logger log = LoggerFactory.getLogger(KafkaEventForwarder.class);

    private final KafkaTemplate<String, String> kafka;
    private final EventJsonCodec codec;
    private final NotificationEventFactory notifications;
    private final ThreadPoolExecutor forwarding;

    public KafkaEventForwarder(
            KafkaTemplate<String, String> kafka, EventJsonCodec codec, NotificationEventFactory notifications) {
        this.kafka = kafka;
        this.codec = codec;
        this.notifications = notifications;
        this.forwarding = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                Thread.ofPlatform().name("ctip-kafka-forwarder").factory(),
                (task, executor) -> log.warn("Kafka 轉發佇列已滿({} 筆),丟棄一個事件;broker 是否不可用?", QUEUE_CAPACITY));
    }

    @EventListener
    public void onDomainEvent(DomainEventEnvelope envelope) {
        forwarding.execute(() -> forward(envelope));
    }

    private void forward(DomainEventEnvelope envelope) {
        forwardToDomainTopic(envelope);
        forwardNotificationProjection(envelope);
    }

    private void forwardToDomainTopic(DomainEventEnvelope envelope) {
        try {
            send(KafkaTopics.of(envelope.event()), envelope.eventId().toString(), codec.encode(envelope));
        } catch (RuntimeException e) {
            log.warn(
                    "轉發 domain event 到 Kafka 失敗,只記錄不影響已提交的業務操作(§13.1 規則 7);event={} 原因={}",
                    envelope.eventId(),
                    e.getMessage());
        }
    }

    private void forwardNotificationProjection(DomainEventEnvelope envelope) {
        try {
            Optional<NotificationEvent> projection = notifications.from(contextOf(envelope), envelope.event());
            projection.ifPresent(event ->
                    send(KafkaTopics.NOTIFICATION_EVENTS, event.eventId().toString(), codec.encode(event)));
        } catch (RuntimeException e) {
            log.warn("轉發通知投影到 Kafka 失敗;event={} 原因={}", envelope.eventId(), e.getMessage());
        }
    }

    /**
     * key 為 {@code eventId}:同一個事件重送必然落在同一個 partition,消費端的去重
     * (§13.1 規則 5)因此不必跨 partition 協調。
     */
    private void send(String topic, String key, String payload) {
        kafka.send(topic, key, payload).whenComplete((result, error) -> {
            if (error != null) {
                log.warn("Kafka 送出失敗:topic={} key={} 原因={}", topic, key, error.getMessage());
            }
        });
    }

    private static EventContext contextOf(DomainEventEnvelope envelope) {
        return new EventContext(envelope.eventId(), envelope.occurredAt(), envelope.traceId());
    }

    @PreDestroy
    void shutdown() {
        forwarding.shutdown();
    }
}
