package com.ctip.application.ingestion;

import com.ctip.application.stix.StixProjectionWriter;
import com.ctip.sdk.RawThreatRecord;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 一個批次的完整執行:交易內攝取({@link IngestionBatchProcessor},一批一交易)
 * → 提交後寫出 STIX 投影({@link StixProjectionWriter})。
 * 寫出必須在交易外:stix_objects 的 FK 指向 indicators(需先提交),
 * 且投影失敗不得使 ingestion 失敗(§7.8.6;ADR 0005)。
 */
@Service
public class IngestionBatchExecutor {

    private final IngestionBatchProcessor processor;
    private final StixProjectionWriter projections;

    public IngestionBatchExecutor(IngestionBatchProcessor processor, StixProjectionWriter projections) {
        this.processor = processor;
        this.projections = projections;
    }

    public int batchSize() {
        return processor.batchSize();
    }

    public BatchOutcome execute(SourceContext source, IngestionRun run, List<RawThreatRecord> batch) {
        BatchOutcome outcome = processor.process(source, run, batch);
        projections.writeAll(outcome.projections());
        return outcome;
    }

    /** 單筆(手動提交):交易內攝取,提交後寫出 STIX 投影,規則與批次相同。 */
    public RecordOutcome executeOne(SourceContext source, IngestionRun run, RawThreatRecord record) {
        RecordOutcome outcome = processor.processOne(source, run, record);
        projections.writeAll(outcome.projections());
        return outcome;
    }
}
