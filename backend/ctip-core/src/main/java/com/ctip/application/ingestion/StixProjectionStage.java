package com.ctip.application.ingestion;

import com.ctip.application.stix.StixProjectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stage 8 StixProject(§8.2、§7.8):建構 STIX 投影放入 context。
 * 投影<strong>建構</strong>在此;<strong>寫出</strong>由 {@link IngestionBatchExecutor} 於批次交易提交後執行
 * (stix_objects 的 FK 指向 indicators,且投影失敗不得使 ingestion 失敗——§7.8.6;ADR 0005)。
 * 任何投影錯誤只記錄並繼續,絕不 reject 該筆。
 *
 * <p>投影組的內容(indicator + 每個來源記錄的 observed-data + 每個來源的 identity)由
 * {@link StixProjectionFactory} 產生——管理端點的重建走的是同一個工廠(ADR 0031)。
 */
public final class StixProjectionStage implements IngestionStage {

    private static final Logger log = LoggerFactory.getLogger(StixProjectionStage.class);

    private final StixProjectionFactory projections;

    public StixProjectionStage(StixProjectionFactory projections) {
        this.projections = projections;
    }

    @Override
    public String name() {
        return "StixProject";
    }

    @Override
    public IngestionContext execute(IngestionContext context) {
        try {
            projections.projectionsFor(context.indicator().snapshot()).forEach(context::addStixProjection);
        } catch (RuntimeException e) {
            context.clearStixProjections();
            log.warn("STIX 投影建構失敗,只記錄不影響 ingestion(§7.8.6):{}", context.normalizedValue(), e);
        }
        return context;
    }
}
