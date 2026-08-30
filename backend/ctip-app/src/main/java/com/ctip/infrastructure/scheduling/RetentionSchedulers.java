package com.ctip.infrastructure.scheduling;

import com.ctip.infrastructure.retention.RetentionService;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 六項資料保留清理的排程(docs/spec/08-ingestion-sdk.md §8.7 的後六列;政策見 13 §13.4)。
 *
 * <p>與其他排程類別同一個形狀:只做一件事、只呼叫一個方法,任務類別本身不含業務邏輯。
 * 分批、筆數記錄與失敗隔離都在 {@link RetentionService}。
 *
 * <p>bean 由 {@code RetentionConfig} 建立(不是 {@code @Component}):它與清理連線一起
 * 條件裝配,而排程總開關 {@code SCHEDULER_ENABLED} 在那裡一併判斷。
 */
public class RetentionSchedulers {

    private final RetentionService retention;

    public RetentionSchedulers(RetentionService retention) {
        this.retention = retention;
    }

    /** 稽核保留清理:每週日 01:00(AUDIT_CLEANUP_CRON)。 */
    @Scheduled(cron = "${ctip.retention.crons.audit}")
    void purgeAuditLogs() {
        retention.purgeAuditLogs();
    }

    /** 原始 payload 清理:每日 01:30(PAYLOAD_CLEANUP_CRON)。 */
    @Scheduled(cron = "${ctip.retention.crons.raw-payload}")
    void clearRawPayloads() {
        retention.clearRawPayloads();
    }

    /** 拒絕記錄清理:每日 01:40(REJECTION_CLEANUP_CRON)。 */
    @Scheduled(cron = "${ctip.retention.crons.rejection}")
    void purgeRejections() {
        retention.purgeRejections();
    }

    /** Bloom artifact 清理:每日 01:50(BLOOM_ARTIFACT_CLEANUP_CRON)。 */
    @Scheduled(cron = "${ctip.retention.crons.bloom-artifact}")
    void pruneBloomArtifacts() {
        retention.pruneBloomArtifacts();
    }

    /** Webhook 送達記錄清理:每日 02:10(DELIVERY_CLEANUP_CRON)。 */
    @Scheduled(cron = "${ctip.retention.crons.delivery}")
    void purgeWebhookDeliveries() {
        retention.purgeWebhookDeliveries();
    }

    /** EXPIRED indicator 軟刪除:每日 02:20(INDICATOR_CLEANUP_CRON)。 */
    @Scheduled(cron = "${ctip.retention.crons.indicator}")
    void softDeleteExpiredIndicators() {
        retention.softDeleteExpiredIndicators();
    }
}
