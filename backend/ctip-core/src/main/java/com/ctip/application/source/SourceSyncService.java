package com.ctip.application.source;

import com.ctip.application.port.AdapterRegistryPort;
import com.ctip.application.port.ClockPort;
import com.ctip.application.port.SourceRepository;
import com.ctip.domain.source.Source;
import com.ctip.sdk.FetchContext;
import com.ctip.sdk.FetchResult;
import com.ctip.sdk.ThreatSourceAdapter;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 來源同步(docs/spec/08-ingestion-sdk.md §8.5–8.6):逐一處理各來源,
 * 每個來源獨立 try/catch——單一來源故障不得使整個 ingestion 系統停止。
 * adapter fetch 絕不包在資料庫交易內;健康記錄由 {@link SourceHealthService} 以獨立交易處理。
 */
@Service
public class SourceSyncService {

    /** 單次 fetch 的筆數上限與單輪分頁上限(防止行為異常的 adapter 造成無界迴圈)。 */
    private static final int MAX_RECORDS_PER_FETCH = 1000;

    private static final int MAX_PAGES_PER_RUN = 1000;
    private static final Logger log = LoggerFactory.getLogger(SourceSyncService.class);

    private final SourceRepository sources;
    private final AdapterRegistryPort adapters;
    private final SourceHealthService health;
    private final ClockPort clock;

    public SourceSyncService(
            SourceRepository sources, AdapterRegistryPort adapters, SourceHealthService health, ClockPort clock) {
        this.sources = sources;
        this.adapters = adapters;
        this.health = health;
        this.clock = clock;
    }

    /** 排程進入點:同步所有 enabled、syncable 且依 recommendedInterval 已到期的來源。 */
    public List<SourceSyncOutcome> syncDueSources() {
        Instant now = clock.now();
        return syncEach(sources.findEnabledSyncable().stream()
                .filter(source -> source.isDueForSync(now))
                .toList());
    }

    private List<SourceSyncOutcome> syncEach(List<Source> dueSources) {
        List<SourceSyncOutcome> outcomes = new ArrayList<>();
        for (Source source : dueSources) {
            outcomes.add(syncOne(source));
        }
        return outcomes;
    }

    private SourceSyncOutcome syncOne(Source source) {
        int fetched = 0;
        try {
            ThreatSourceAdapter adapter = adapters.find(source.snapshot().sourceType())
                    .orElseThrow(() -> new IllegalStateException(
                            "來源沒有對應的 adapter:" + source.snapshot().sourceType()));
            Instant started = clock.now();
            FetchContext context = source.fetchContext(Map.of(), MAX_RECORDS_PER_FETCH);
            FetchResult page = adapter.fetch(context);
            fetched += page.records().size();
            int pages = 1;
            while (page.hasMore() && pages < MAX_PAGES_PER_RUN) {
                context = new FetchContext(context.since(), page.nextCursor(), context.config(), MAX_RECORDS_PER_FETCH);
                page = adapter.fetch(context);
                fetched += page.records().size();
                pages++;
            }
            Duration latency = Duration.between(started, clock.now());
            health.recordSuccess(source, fetched, latency, page.hasMore() ? page.nextCursor() : null);
            return SourceSyncOutcome.success(source.id(), fetched);
        } catch (RuntimeException e) {
            log.warn("來源同步失敗,不影響其他來源:{}", source.id(), e);
            health.recordFailure(source, e.getMessage());
            return SourceSyncOutcome.failure(source.id(), fetched, e.getMessage());
        }
    }
}
