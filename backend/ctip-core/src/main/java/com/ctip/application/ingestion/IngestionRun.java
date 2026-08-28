package com.ctip.application.ingestion;

import java.util.UUID;

/**
 * 一次 ingestion 執行的來歷與配額(docs/spec/08-ingestion-sdk.md §8.2)。
 *
 * <p>三種來歷各自對應 {@code ingestion_rejections} 的不同關聯欄位:
 * 排程同步帶 {@code sourceSyncId}、檔案匯入帶 {@code importJobId}、單筆手動提交兩者皆無。
 * {@code remainingQuota} 為 null 代表不在此層限量(單筆提交在進 pipeline 前就已扣減完畢)。
 *
 * @param sourceSyncId source_sync 列;非排程同步為 null
 * @param importJobId import_jobs 列;非匯入為 null
 * @param remainingQuota 本次執行還能接受幾筆;越界者逐筆記為 {@code QUOTA_EXCEEDED}(§9.7)
 */
public record IngestionRun(UUID sourceSyncId, UUID importJobId, Integer remainingQuota) {

    public static IngestionRun forSourceSync(UUID sourceSyncId) {
        return new IngestionRun(sourceSyncId, null, null);
    }

    public static IngestionRun forImport(UUID importJobId, Integer remainingQuota) {
        return new IngestionRun(null, importJobId, remainingQuota);
    }

    /** 單筆手動提交:每日配額已於進 pipeline 前扣減(§9.7 → 429),此處不再限量。 */
    public static IngestionRun forManualSubmission() {
        return new IngestionRun(null, null, null);
    }

    BatchState newBatchState() {
        return new BatchState(sourceSyncId, remainingQuota);
    }
}
