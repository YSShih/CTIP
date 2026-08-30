package com.ctip.application.ingestion;

import com.ctip.application.observability.IngestionMetrics;
import io.micrometer.core.instrument.Timer;
import java.util.List;

/**
 * 依註冊順序執行 stage(docs/spec/08-ingestion-sdk.md §8.2);
 * 任一 stage 拒絕後短路。M2 插入 BloomUpdate / SearchIndex 時只改 IngestionPipelineConfig 的 List.of。
 *
 * <p>每個 stage 各自計時({@code ctip.ingestion.stage.duration{stage}},13 §13.6)——
 * 這正是 §8.2 選擇「顯式 stage 列表而非 Template Method」的直接收益,
 * 計時器在建構時就依 stage 清單註冊完畢。
 */
public final class IngestionPipeline {

    private final List<IngestionStage> stages;
    private final IngestionMetrics metrics;

    public IngestionPipeline(List<IngestionStage> stages, IngestionMetrics metrics) {
        this.stages = List.copyOf(stages);
        this.metrics = metrics;
        metrics.registerStages(stageNames());
    }

    public IngestionContext run(IngestionContext context) {
        IngestionContext current = context;
        for (IngestionStage stage : stages) {
            if (current.rejected()) {
                break;
            }
            Timer.Sample sample = Timer.start();
            try {
                current = stage.execute(current);
            } finally {
                sample.stop(metrics.stageTimer(stage.name()));
            }
        }
        return current;
    }

    public List<String> stageNames() {
        return stages.stream().map(IngestionStage::name).toList();
    }
}
