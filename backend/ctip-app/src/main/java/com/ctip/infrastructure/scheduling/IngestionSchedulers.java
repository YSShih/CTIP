package com.ctip.infrastructure.scheduling;

import com.ctip.application.indicator.IndicatorExpiryService;
import com.ctip.application.ingestion.IngestionSettings;
import com.ctip.application.source.SourceSyncService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * M1 的三個排程任務(docs/spec/08-ingestion-sdk.md §8.7):每個任務只做一件事、
 * 只呼叫 application service 的方法,任務類別不含業務邏輯。cron 皆可由環境變數覆寫。
 */
@Component
@ConditionalOnProperty(prefix = "ctip.scheduler", name = "enabled", havingValue = "true")
public class IngestionSchedulers {

    private final SourceSyncService sourceSync;
    private final IndicatorExpiryService expiry;
    private final IngestionSettings ingestion;

    public IngestionSchedulers(
            SourceSyncService sourceSync, IndicatorExpiryService expiry, IngestionSettings ingestion) {
        this.sourceSync = sourceSync;
        this.expiry = expiry;
        this.ingestion = ingestion;
    }

    /** 來源同步:每來源依 recommendedInterval 決定是否到期(SOURCE_SYNC_CRON 是掃描節奏)。 */
    @Scheduled(cron = "${ctip.scheduler.source-sync-cron}")
    void syncDueSources() {
        if (ingestion.enabled()) {
            sourceSync.syncDueSources();
        }
    }

    /** IOC 過期標記:每日 03:00(IOC_EXPIRY_CRON)。 */
    @Scheduled(cron = "${ctip.scheduler.ioc-expiry-cron}")
    void markExpiredIndicators() {
        expiry.markExpiredIndicators();
    }

    /** 失敗 ingestion 重試:每 15 分鐘(INGESTION_RETRY_CRON)。 */
    @Scheduled(cron = "${ctip.scheduler.ingestion-retry-cron}")
    void retryFailedSources() {
        if (ingestion.enabled()) {
            sourceSync.retryFailedSources();
        }
    }
}
