package com.ctip.infrastructure.retention;

import com.ctip.application.bloom.BloomRetentionService;
import java.util.function.IntSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 六項資料保留清理(docs/spec/13-platform-ops.md §13.4;排程見 08 §8.7)。
 *
 * <p>每一項都必須:分批(見 {@link RetentionTasks})、<strong>記錄清理筆數</strong>、
 * 且<strong>失敗不影響其他任務</strong>——所以每一項各自 try/catch,而不是六項共用一個。
 *
 * <p>Bloom artifact 那一項委派給 Phase 15 就有的 {@link BloomRetentionService}:
 * 「保留最近 N 份」不是一句 SQL,它必須避開仍被 delta 鏈依賴的 full snapshot、
 * 並一併刪除檔案系統上的 artifact。把那段邏輯照抄成 SQL 會是保留策略的第二份實作,
 * 而寫錯的後果是 {@code /sync/delta} 斷鏈(ADR 0031)。
 */
public class RetentionService {

    private static final Logger log = LoggerFactory.getLogger(RetentionService.class);

    private final RetentionTasks tasks;
    private final BloomRetentionService bloom;

    public RetentionService(RetentionTasks tasks, BloomRetentionService bloom) {
        this.tasks = tasks;
        this.bloom = bloom;
    }

    public int purgeAuditLogs() {
        return run("audit_logs", tasks::purgeAuditLogs);
    }

    public int clearRawPayloads() {
        return run("indicator_sources.raw_payload", tasks::clearRawPayloads);
    }

    public int purgeRejections() {
        return run("ingestion_rejections", tasks::purgeRejections);
    }

    public int purgeWebhookDeliveries() {
        return run("webhook_deliveries", tasks::purgeWebhookDeliveries);
    }

    public int softDeleteExpiredIndicators() {
        return run("indicators(EXPIRED 軟刪除)", tasks::softDeleteExpiredIndicators);
    }

    public int pruneBloomArtifacts() {
        return run("bloom artifact", bloom::purgeAll);
    }

    /** 六項全跑(整合測試與手動維運用);單項失敗只記錄,其餘照跑。 */
    public RetentionReport runAll() {
        return new RetentionReport(
                purgeAuditLogs(),
                clearRawPayloads(),
                purgeRejections(),
                purgeWebhookDeliveries(),
                softDeleteExpiredIndicators(),
                pruneBloomArtifacts());
    }

    private static int run(String what, IntSupplier task) {
        try {
            int cleaned = task.getAsInt();
            log.info("保留清理:{} 清掉 {} 列", what, cleaned);
            return cleaned;
        } catch (RuntimeException e) {
            log.error("保留清理失敗:{}(其餘任務不受影響)", what, e);
            return 0;
        }
    }
}
