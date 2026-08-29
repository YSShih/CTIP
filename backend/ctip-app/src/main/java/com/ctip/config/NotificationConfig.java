package com.ctip.config;

import com.ctip.application.notification.NotificationEventFactory;
import com.ctip.application.notification.NotificationService;
import com.ctip.application.notification.WebhookDeliveryService;
import com.ctip.application.port.SecretCipherPort;
import com.ctip.infrastructure.kafka.EventJsonCodec;
import com.ctip.infrastructure.kafka.KafkaEventForwarder;
import com.ctip.infrastructure.kafka.KafkaTopics;
import com.ctip.infrastructure.kafka.NotificationEventConsumer;
import com.ctip.infrastructure.notification.InProcessEventForwarder;
import com.ctip.infrastructure.scheduling.NotificationSchedulers;
import com.ctip.infrastructure.security.AesGcmSecretCipher;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * 通知與事件傳輸的裝配(docs/spec/13-platform-ops.md §13.1、§13.2)。
 *
 * <p>兩條轉發路徑<strong>互斥</strong>,由 {@code NOTIFICATION_TRANSPORT} 決定,
 * 比照 {@code SearchConfig} / {@code RateLimitConfig} 的作法:
 * {@code in-process} 時完全不建立任何 Kafka bean——mvp/dev 的 compose 不啟動 broker,
 * 憑空多一條打不通的路只會讓每個事件先等一次逾時。
 *
 * <p>兩條路徑最終都呼叫同一個 {@link NotificationService#dispatch},
 * 因此副作用與冪等只有一份實作。
 */
@Configuration(proxyBeanMethods = false)
class NotificationConfig {

    /** webhook 簽章密鑰的 AES-GCM 加解密(不變量 W2 定調;ADR 0021)。 */
    @Bean
    SecretCipherPort secretCipherPort(CtipProperties properties) {
        return new AesGcmSecretCipher(properties.notification().webhookSecretKek());
    }

    /** 送達重試(每 5 分鐘;08 §8.7 的 {@code NOTIFICATION_RETRY_CRON})。 */
    @Bean
    @ConditionalOnProperty(prefix = "ctip.scheduler", name = "enabled", havingValue = "true")
    NotificationSchedulers notificationSchedulers(WebhookDeliveryService deliveries, CtipProperties properties) {
        return new NotificationSchedulers(deliveries, properties.notification().retryBatchSize());
    }

    @Bean
    @ConditionalOnProperty(name = "ctip.notification.transport", havingValue = "in-process", matchIfMissing = true)
    InProcessEventForwarder inProcessEventForwarder(
            NotificationEventFactory factory, NotificationService notifications) {
        return new InProcessEventForwarder(factory, notifications);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "ctip.notification.transport", havingValue = "kafka")
    static class KafkaNotificationConfig {

        /**
         * 六個 topic(§13.1)。{@code KafkaAdmin} 會在啟動時嘗試建立,broker 還沒起來時只記錄
         * ——建立 topic 失敗不得使應用啟動失敗(§13.1 規則 7 的同一條理由)。
         *
         * <p>單一 broker(compose 的 KRaft 單節點),故 replica 數為 1;分割數 3 讓
         * 消費端日後可以水平擴充而不必重建 topic。
         *
         * <p>型別必須是 {@link KafkaAdmin.NewTopics} 而不是 {@code List<NewTopic>}:
         * {@code KafkaAdmin} 只會去找 {@code NewTopic} 與 {@code NewTopics} 兩種型別的 bean,
         * 一個 {@code List<NewTopic>} bean <strong>完全不會被看到</strong>——topic 於是只能靠
         * broker 的 auto-create 產生(分割數與副本數變成 broker 預設值),而且在關閉
         * auto-create 的正式環境會直接沒有 topic。實測到的。
         */
        @Bean
        KafkaAdmin.NewTopics ctipTopics() {
            NewTopic[] topics = KafkaTopics.ALL.stream()
                    .map(name ->
                            TopicBuilder.name(name).partitions(3).replicas(1).build())
                    .toArray(NewTopic[]::new);
            return new KafkaAdmin.NewTopics(topics);
        }

        @Bean
        KafkaEventForwarder kafkaEventForwarder(
                KafkaTemplate<String, String> kafka, EventJsonCodec codec, NotificationEventFactory factory) {
            return new KafkaEventForwarder(kafka, codec, factory);
        }

        @Bean
        NotificationEventConsumer notificationEventConsumer(EventJsonCodec codec, NotificationService notifications) {
            return new NotificationEventConsumer(codec, notifications);
        }
    }
}
