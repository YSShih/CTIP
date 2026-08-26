package com.ctip.application.ingestion;

import com.ctip.application.port.ClockPort;
import com.ctip.application.port.SourceRepository;
import com.ctip.application.port.StixObjectPort;
import com.ctip.domain.indicator.IndicatorSnapshot;
import com.ctip.domain.source.Source;
import com.ctip.domain.source.SourceId;
import com.ctip.domain.stix.StixIndicatorProjector;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stage 8 StixProject(§8.2、§7.8):建構 STIX indicator 投影放入 context。
 * 投影**建構**在此;**寫出**由 {@link IngestionBatchExecutor} 於批次交易提交後執行
 * (stix_objects 的 FK 指向 indicators,且投影失敗不得使 ingestion 失敗——§7.8.6;ADR 0005)。
 * 任何投影錯誤只記錄並繼續,絕不 reject 該筆。
 */
public final class StixProjectionStage implements IngestionStage {

    private static final Logger log = LoggerFactory.getLogger(StixProjectionStage.class);

    private final SourceRepository sources;
    private final StixObjectPort stixObjects;
    private final ClockPort clock;

    public StixProjectionStage(SourceRepository sources, StixObjectPort stixObjects, ClockPort clock) {
        this.sources = sources;
        this.stixObjects = stixObjects;
        this.clock = clock;
    }

    @Override
    public String name() {
        return "StixProject";
    }

    @Override
    public IngestionContext execute(IngestionContext context) {
        try {
            IndicatorSnapshot snapshot = context.indicator().snapshot();
            Instant now = clock.now();
            Instant created = stixObjects
                    .findCreated(StixIndicatorProjector.stixId(snapshot))
                    .orElse(now);
            context.stixProjection(StixIndicatorProjector.project(snapshot, sourceNames(snapshot), created, now));
        } catch (RuntimeException e) {
            log.warn("STIX 投影建構失敗,只記錄不影響 ingestion(§7.8.6):{}", context.normalizedValue(), e);
        }
        return context;
    }

    private Map<SourceId, String> sourceNames(IndicatorSnapshot snapshot) {
        Map<SourceId, String> names = new HashMap<>();
        for (var record : snapshot.sources()) {
            sources.findById(record.sourceId())
                    .map(Source::snapshot)
                    .ifPresent(s -> names.put(record.sourceId(), s.displayName()));
        }
        return names;
    }
}
