package com.ctip.application.source;

import com.ctip.application.ingestion.BatchOutcome;
import com.ctip.application.ingestion.IngestionBatchProcessor;
import com.ctip.application.ingestion.SourceContext;
import com.ctip.application.port.AdapterRegistryPort;
import com.ctip.application.port.ClockPort;
import com.ctip.application.port.SourceRepository;
import com.ctip.application.source.SourceSyncRecorder.SyncRun;
import com.ctip.domain.source.Source;
import com.ctip.sdk.FetchContext;
import com.ctip.sdk.FetchResult;
import com.ctip.sdk.RawThreatRecord;
import com.ctip.sdk.ThreatSourceAdapter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 來源同步編排(docs/spec/08-ingestion-sdk.md §8.2、§8.5、03 §3.4):逐一處理各來源,
 * 每個來源獨立 try/catch——單一來源故障不得使整個 ingestion 系統停止。
 * adapter fetch 絕不在交易內;抓滿一批(INGESTION_BATCH_SIZE)即交給批次處理器(一批一交易)。
 * 記錄面(source_sync、健康、事件)由 {@link SourceSyncRecorder} 負責。
 */
@Service
public class SourceSyncService {

    /** 單次 fetch 的筆數上限與單輪分頁上限(防止行為異常的 adapter 造成無界迴圈)。 */
    private static final int MAX_RECORDS_PER_FETCH = 1000;

    private static final int MAX_PAGES_PER_RUN = 1000;
    private static final Logger log = LoggerFactory.getLogger(SourceSyncService.class);

    private final SourceRepository sources;
    private final AdapterRegistryPort adapters;
    private final IngestionBatchProcessor batchProcessor;
    private final SourceSyncRecorder recorder;
    private final ClockPort clock;

    public SourceSyncService(
            SourceRepository sources,
            AdapterRegistryPort adapters,
            IngestionBatchProcessor batchProcessor,
            SourceSyncRecorder recorder,
            ClockPort clock) {
        this.sources = sources;
        this.adapters = adapters;
        this.batchProcessor = batchProcessor;
        this.recorder = recorder;
        this.clock = clock;
    }

    /** 排程進入點:同步所有 enabled、syncable 且依 recommendedInterval 已到期的來源。 */
    public List<SourceSyncOutcome> syncDueSources() {
        var now = clock.now();
        return syncEach(source -> source.isDueForSync(now));
    }

    /** 失敗重試排程(§8.7):針對連續失敗中的來源,不等 recommendedInterval。 */
    public List<SourceSyncOutcome> retryFailedSources() {
        return syncEach(source -> source.health().consecutiveFailures() > 0);
    }

    private List<SourceSyncOutcome> syncEach(Predicate<Source> due) {
        List<SourceSyncOutcome> outcomes = new ArrayList<>();
        for (Source source : sources.findEnabledSyncable()) {
            if (due.test(source)) {
                outcomes.add(syncOne(source));
            }
        }
        return outcomes;
    }

    private SourceSyncOutcome syncOne(Source source) {
        SyncRun run = recorder.started(source.id());
        RunTotals totals = new RunTotals();
        try {
            String finalCursor = fetchAndIngest(source, run.syncId(), totals);
            recorder.completed(source, run, totals.fetched, totals.batches, finalCursor);
            return SourceSyncOutcome.success(source.id(), totals.fetched, totals.batches);
        } catch (RuntimeException e) {
            log.warn("來源同步失敗,不影響其他來源:{}", source.id(), e);
            String masked = recorder.failed(source, run, totals.fetched, totals.batches, e);
            return SourceSyncOutcome.failure(source.id(), totals.fetched, masked);
        }
    }

    /** fetch 分頁迴圈(交易外);滿一批即進 pipeline。回傳應保存的續抓游標(抓到底為 null)。 */
    private String fetchAndIngest(Source source, UUID syncId, RunTotals totals) {
        ThreatSourceAdapter adapter = adapters.find(source.snapshot().sourceType())
                .orElseThrow(() -> new IllegalStateException(
                        "來源沒有對應的 adapter:" + source.snapshot().sourceType()));
        SourceContext sourceContext = SourceContext.publicFeed(source);
        List<RawThreatRecord> buffer = new ArrayList<>();
        FetchContext context = source.fetchContext(Map.of(), MAX_RECORDS_PER_FETCH);
        FetchResult page = adapter.fetch(context);
        int pages = 1;
        while (true) {
            totals.fetched += page.records().size();
            buffer.addAll(page.records());
            while (buffer.size() >= batchProcessor.batchSize()) {
                totals.add(batchProcessor.process(sourceContext, syncId, drain(buffer, batchProcessor.batchSize())));
            }
            if (!page.hasMore() || pages >= MAX_PAGES_PER_RUN) {
                break;
            }
            context = new FetchContext(context.since(), page.nextCursor(), context.config(), MAX_RECORDS_PER_FETCH);
            page = adapter.fetch(context);
            pages++;
        }
        if (!buffer.isEmpty()) {
            totals.add(batchProcessor.process(sourceContext, syncId, drain(buffer, buffer.size())));
        }
        return page.hasMore() ? page.nextCursor() : null;
    }

    private static List<RawThreatRecord> drain(List<RawThreatRecord> buffer, int count) {
        List<RawThreatRecord> chunk = List.copyOf(buffer.subList(0, count));
        buffer.subList(0, count).clear();
        return chunk;
    }

    /** 單輪累計(fetch 筆數與批次計數)。 */
    private static final class RunTotals {
        private int fetched;
        private BatchOutcome batches = BatchOutcome.EMPTY;

        private void add(BatchOutcome outcome) {
            batches = batches.plus(outcome);
        }
    }
}
