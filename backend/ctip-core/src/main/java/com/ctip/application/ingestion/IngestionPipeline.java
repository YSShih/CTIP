package com.ctip.application.ingestion;

import java.util.List;

/**
 * 依註冊順序執行 stage(docs/spec/08-ingestion-sdk.md §8.2);
 * 任一 stage 拒絕後短路。M2 插入 BloomUpdate / SearchIndex 時只改 IngestionPipelineConfig 的 List.of。
 */
public final class IngestionPipeline {

    private final List<IngestionStage> stages;

    public IngestionPipeline(List<IngestionStage> stages) {
        this.stages = List.copyOf(stages);
    }

    public IngestionContext run(IngestionContext context) {
        IngestionContext current = context;
        for (IngestionStage stage : stages) {
            if (current.rejected()) {
                break;
            }
            current = stage.execute(current);
        }
        return current;
    }

    public List<String> stageNames() {
        return stages.stream().map(IngestionStage::name).toList();
    }
}
