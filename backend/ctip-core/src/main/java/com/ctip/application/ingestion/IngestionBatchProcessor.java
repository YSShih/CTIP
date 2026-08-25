package com.ctip.application.ingestion;

import com.ctip.application.port.RejectionLogPort;
import com.ctip.sdk.RawThreatRecord;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 批次與交易邊界(docs/spec/08-ingestion-sdk.md §8.2):每批一個交易;
 * 單筆以 try/catch 包住,失敗只丟棄該筆並記錄至 ingestion_rejections,不使整批 rollback。
 * 不使用 @Transactional(noRollbackFor=...)。
 */
@Service
public class IngestionBatchProcessor {

    private static final Logger log = LoggerFactory.getLogger(IngestionBatchProcessor.class);

    private final IngestionPipeline pipeline;
    private final RejectionLogPort rejections;
    private final IngestionSettings settings;

    public IngestionBatchProcessor(
            IngestionPipeline pipeline, RejectionLogPort rejections, IngestionSettings settings) {
        this.pipeline = pipeline;
        this.rejections = rejections;
        this.settings = settings;
    }

    /** 批次大小(INGESTION_BATCH_SIZE):呼叫端以此切批,一批一交易。 */
    public int batchSize() {
        return settings.batchSize();
    }

    @Transactional
    public BatchOutcome process(SourceContext source, UUID sourceSyncId, List<RawThreatRecord> batch) {
        BatchState state = new BatchState(sourceSyncId, null);
        int accepted = 0;
        int rejected = 0;
        int merged = 0;
        for (RawThreatRecord raw : batch) {
            IngestionContext context = new IngestionContext(raw, source, state);
            try {
                pipeline.run(context);
            } catch (RuntimeException e) {
                log.warn("單筆 ingestion 失敗,記錄後繼續(不 rollback 整批):{}", raw.rawValue(), e);
                context.reject(RejectionReason.MALFORMED_VALUE, "非預期錯誤:" + e.getMessage());
            }
            if (context.rejected()) {
                rejections.record(new RejectedRecord(
                        source.sourceId(),
                        sourceSyncId,
                        raw.rawValue(),
                        raw.declaredType(),
                        context.rejectionReason(),
                        context.rejectionDetail()));
                rejected++;
            } else {
                accepted++;
                if (context.merged()) {
                    merged++;
                }
            }
        }
        return new BatchOutcome(accepted, rejected, merged);
    }
}
