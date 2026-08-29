package com.ctip.application.ingestion;

import com.ctip.application.search.SearchIndexWriter;
import com.ctip.application.stix.StixProjectionWriter;
import com.ctip.sdk.RawThreatRecord;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 一個批次的完整執行:交易內攝取({@link IngestionBatchProcessor},一批一交易)
 * → 提交後寫出 STIX 投影({@link StixProjectionWriter})與搜尋索引({@link SearchIndexWriter})。
 * 寫出必須在交易外:stix_objects 的 FK 指向 indicators(需先提交),
 * 且投影失敗不得使 ingestion 失敗(§7.8.6;ADR 0005);
 * 搜尋索引同理——它是外部系統,失敗不得使 ingestion 失敗(§13.7)。
 */
@Service
public class IngestionBatchExecutor {

    private final IngestionBatchProcessor processor;
    private final StixProjectionWriter projections;
    private final SearchIndexWriter searchIndex;

    public IngestionBatchExecutor(
            IngestionBatchProcessor processor, StixProjectionWriter projections, SearchIndexWriter searchIndex) {
        this.processor = processor;
        this.projections = projections;
        this.searchIndex = searchIndex;
    }

    public int batchSize() {
        return processor.batchSize();
    }

    public BatchOutcome execute(SourceContext source, IngestionRun run, List<RawThreatRecord> batch) {
        BatchOutcome outcome = processor.process(source, run, batch);
        projections.writeAll(outcome.projections());
        searchIndex.indexAll(outcome.indexTargets());
        return outcome;
    }

    /** 單筆(手動提交):交易內攝取,提交後寫出 STIX 投影與搜尋索引,規則與批次相同。 */
    public RecordOutcome executeOne(SourceContext source, IngestionRun run, RawThreatRecord record) {
        RecordOutcome outcome = processor.processOne(source, run, record);
        projections.writeAll(outcome.projections());
        if (outcome.indicator() != null) {
            searchIndex.indexAll(List.of(outcome.indicator().id()));
        }
        return outcome;
    }
}
