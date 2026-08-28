package com.ctip.application.ingestion;

import com.ctip.application.identity.AuthenticatedIdentity;
import com.ctip.application.plan.QuotaService;
import com.ctip.application.plan.RequestSizeLimitExceededException;
import com.ctip.application.port.ImportJobRepository;
import com.ctip.application.port.ImportPayloadParserPort;
import com.ctip.domain.tenant.TenantId;
import com.ctip.sdk.RawThreatRecord;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 批次匯入(docs/spec/09-api.md §9.7 {@code POST /api/v1/iocs/import})。
 *
 * <p>流程:解碼 → 依方案檢查單檔筆數上限(413)→ 建立 {@code import_jobs} 列(PENDING)→
 * 回 {@code 202} + jobId → 交由 {@link ImportJobRunner} 非同步處理。
 * 解碼在同步階段完成,因為筆數上限必須在回 202 之前就能判定;
 * 真正的寫入(可能數十萬筆)才非同步。
 *
 * <p>匯入的 IOC 一律是<strong>租戶私有</strong>({@code AMBER}):§9.7 只為單筆提交定義了
 * {@code ioc:publish} 的擁有權轉移語意,批次公開沒有定義,猜一個實作下去比不做更糟(規則 16)。
 */
@Service
public class ImportService {

    /**
     * 解碼階段的硬上限,與方案無關。
     *
     * <p>方案上限最高是 ENTERPRISE 的 500,000;沒有這道硬上限,一個超大檔案在「檢查方案上限」
     * 之前就已經全部解進記憶體了——413 要能保護自己,就必須在解碼時就停手。
     */
    private static final int MAX_DECODED_ROWS = 1_000_000;

    private final ImportPayloadParserPort parser;
    private final ImportJobRepository jobs;
    private final ImportJobRunner runner;
    private final QuotaService quotas;
    private final ImportJobFactory jobFactory;

    public ImportService(
            ImportPayloadParserPort parser,
            ImportJobRepository jobs,
            ImportJobRunner runner,
            QuotaService quotas,
            ImportJobFactory jobFactory) {
        this.parser = parser;
        this.jobs = jobs;
        this.runner = runner;
        this.quotas = quotas;
        this.jobFactory = jobFactory;
    }

    /**
     * <strong>刻意不加 {@code @Transactional}</strong>:{@code runner.run} 是 {@code @Async},
     * 背景執行緒會立刻去讀／寫這一列 job。若整個 submit 包在一個交易裡,PENDING 列在方法回傳前
     * 尚未提交,背景執行緒查不到它、於是以同一個 id 再 INSERT 一次,外層交易提交時直接撞主鍵。
     * 每個 repository 呼叫各自成交易即可。
     */
    public ImportJob submit(ImportFormat format, String payload, AuthenticatedIdentity submitter) {
        List<RawThreatRecord> records = parser.parse(format, payload);
        if (records.size() > MAX_DECODED_ROWS) {
            throw new RequestSizeLimitExceededException("Import exceeds the decoder limit of " + MAX_DECODED_ROWS);
        }
        quotas.requireImportRowsWithin(submitter.tenantId(), records.size());
        ImportJob job = jobs.save(jobFactory.pending(submitter, format, records.size()));
        runner.run(job, records);
        return job;
    }

    @Transactional(readOnly = true)
    public Optional<ImportJob> find(ImportJobId id, TenantId tenantId) {
        return jobs.find(id, tenantId);
    }
}
