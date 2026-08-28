package com.ctip.application.ingestion;

import com.ctip.application.plan.QuotaService;
import com.ctip.application.port.ClockPort;
import com.ctip.application.port.ImportJobRepository;
import com.ctip.domain.tenant.TenantId;
import com.ctip.sdk.RawThreatRecord;
import com.ctip.sdk.Tlp;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 匯入的非同步執行(§9.7:回 202 後在背景處理,以 {@code GET /iocs/import/{jobId}} 查進度)。
 *
 * <p>獨立的 bean 而非 {@link ImportService} 的私有方法:Spring 的 {@code @Async} 走 proxy,
 * 自我呼叫不會非同步,那會讓「202 + 背景處理」悄悄退化成同步阻塞。
 *
 * <p>切批進 pipeline,一批一交易——與來源同步完全相同的路徑(§8.3)。
 * 每日配額以 {@link IngestionRun#remainingQuota()} 帶入:越界的記錄逐筆記為
 * {@code QUOTA_EXCEEDED},已接受的部分不因後半超額而整批失敗(§9.7)。
 */
@Service
public class ImportJobRunner {

    private static final Logger log = LoggerFactory.getLogger(ImportJobRunner.class);

    private final IngestionBatchExecutor executor;
    private final ManualSubmissionService submissions;
    private final ImportJobRepository jobs;
    private final QuotaService quotas;
    private final ClockPort clock;

    public ImportJobRunner(
            IngestionBatchExecutor executor,
            ManualSubmissionService submissions,
            ImportJobRepository jobs,
            QuotaService quotas,
            ClockPort clock) {
        this.executor = executor;
        this.submissions = submissions;
        this.jobs = jobs;
        this.quotas = quotas;
        this.clock = clock;
    }

    @Async("importTaskExecutor")
    public void run(ImportJob job, List<RawThreatRecord> records) {
        ImportJob running = jobs.save(job.running(clock.now()));
        try {
            BatchOutcome totals = ingest(running, records);
            quotas.recordManualSubmissions(running.tenantId(), totals.accepted());
            jobs.save(running.finished(totals, clock.now()));
        } catch (RuntimeException e) {
            log.warn("匯入 job 失敗:{}", running.id().value(), e);
            jobs.save(running.failed(e.getMessage(), clock.now()));
        }
    }

    private BatchOutcome ingest(ImportJob job, List<RawThreatRecord> records) {
        TenantId tenantId = job.tenantId();
        // 匯入的 IOC 一律租戶私有(AMBER);批次公開沒有定義的語意(見 ImportService)
        SourceContext source =
                submissions.manualSource(tenantId, Tlp.AMBER, com.ctip.sdk.RedistributionPolicy.INTERNAL_ONLY);
        int remaining = remainingQuota(tenantId);
        BatchOutcome totals = BatchOutcome.EMPTY;
        for (List<RawThreatRecord> batch : batches(records, executor.batchSize())) {
            // 配額必須跨批遞減:BatchState 是一批一個,沿用同一個 IngestionRun
            // 會讓每一批都重新拿到完整餘額,等於整個上限形同虛設
            BatchOutcome outcome =
                    executor.execute(source, IngestionRun.forImport(job.id().value(), remaining), batch);
            remaining = Math.max(0, remaining - outcome.accepted());
            totals = totals.plus(outcome);
        }
        return totals;
    }

    /**
     * 本次匯入還能接受幾筆。無上限的方案以檔案筆數為準——{@code BatchState} 的配額是
     * {@code Integer},給它一個等於總筆數的值等同於不限制,且不必在 pipeline 內特判 null。
     */
    private int remainingQuota(TenantId tenantId) {
        var usage = quotas.manualSubmissionUsage(tenantId);
        return usage.limit().isUnlimited() ? Integer.MAX_VALUE : (int) usage.remaining();
    }

    private static List<List<RawThreatRecord>> batches(List<RawThreatRecord> records, int size) {
        List<List<RawThreatRecord>> batches = new ArrayList<>();
        for (int start = 0; start < records.size(); start += size) {
            batches.add(records.subList(start, Math.min(records.size(), start + size)));
        }
        return batches;
    }
}
