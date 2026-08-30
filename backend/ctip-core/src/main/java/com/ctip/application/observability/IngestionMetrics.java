package com.ctip.application.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 攝取的兩個指標(docs/spec/13-platform-ops.md §13.6):{@code ctip.ingestion.records{result}}
 * 與 {@code ctip.ingestion.stage.duration{stage}}。
 *
 * <p>兩者都在建構時就註冊完畢({@code result} 的三個值固定,stage 清單由 pipeline 交出),
 * 而不是第一次命中才出現:Prometheus 抓不到的序列與「值為 0」是兩回事,
 * 剛啟動的實例若整組指標都不存在,dashboard 與告警規則都會看到 no data。
 */
@Component
public class IngestionMetrics {

    private final MeterRegistry registry;
    private final Map<String, Timer> stageTimers = new ConcurrentHashMap<>();
    private final Counter accepted;
    private final Counter rejected;
    private final Counter merged;

    public IngestionMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.accepted = records("accepted");
        this.rejected = records("rejected");
        this.merged = records("merged");
    }

    /** pipeline 在建構時交出 stage 清單,使每個 stage 的序列一開始就存在。 */
    public void registerStages(Collection<String> stageNames) {
        stageNames.forEach(this::stageTimer);
    }

    public Timer stageTimer(String stageName) {
        return stageTimers.computeIfAbsent(
                stageName,
                name -> Timer.builder(CtipMetricNames.INGESTION_STAGE_DURATION)
                        .description("每個 ingestion pipeline stage 的耗時")
                        .tag("stage", name)
                        .register(registry));
    }

    /** 一批的結果;{@code merged} 是 {@code accepted} 的子集(合併到既有 indicator 的筆數)。 */
    public void recordBatch(int acceptedCount, int rejectedCount, int mergedCount) {
        accepted.increment(acceptedCount);
        rejected.increment(rejectedCount);
        merged.increment(mergedCount);
    }

    /** 單筆(手動提交／匯入的逐筆路徑)。 */
    public void recordOne(boolean isRejected, boolean isMerged) {
        recordBatch(isRejected ? 0 : 1, isRejected ? 1 : 0, isMerged ? 1 : 0);
    }

    private Counter records(String result) {
        return Counter.builder(CtipMetricNames.INGESTION_RECORDS)
                .description("攝取筆數(成功／失敗／合併)")
                .tag("result", result)
                .register(registry);
    }
}
