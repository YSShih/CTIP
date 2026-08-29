package com.ctip.infrastructure.scheduling;

import com.ctip.application.notification.WebhookDeliveryService;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Webhook 送達重試(docs/spec/08-ingestion-sdk.md §8.7「通知重試,每 5 分鐘」,
 * 環境變數 {@code NOTIFICATION_RETRY_CRON})。
 *
 * <p>與 {@code SearchSchedulers} 相同,不是 {@code @Component}:裝配條件
 * ({@code ctip.scheduler.enabled})在 {@code NotificationConfig}。
 * 任務本身只做一件事並呼叫 application service(§8.7 的規則)。
 */
public class NotificationSchedulers {

    private final WebhookDeliveryService deliveries;
    private final int batchSize;

    public NotificationSchedulers(WebhookDeliveryService deliveries, int batchSize) {
        this.deliveries = deliveries;
        this.batchSize = batchSize;
    }

    @Scheduled(cron = "${ctip.notification.retry-cron}")
    void retryPendingDeliveries() {
        deliveries.retryDue(batchSize);
    }
}
