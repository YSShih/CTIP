package com.ctip.application.ingestion;

import com.ctip.application.port.ClockPort;
import com.ctip.application.port.SourceRepository;
import com.ctip.application.port.StixObjectPort;
import com.ctip.domain.indicator.IndicatorSnapshot;
import com.ctip.domain.indicator.IndicatorSourceSnapshot;
import com.ctip.domain.source.Source;
import com.ctip.domain.source.SourceId;
import com.ctip.domain.source.SourceSnapshot;
import com.ctip.domain.stix.StixIdentityProjector;
import com.ctip.domain.stix.StixIndicatorProjector;
import com.ctip.domain.stix.StixObservedDataProjector;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stage 8 StixProject(§8.2、§7.8):建構 STIX 投影放入 context。
 * 投影<strong>建構</strong>在此;<strong>寫出</strong>由 {@link IngestionBatchExecutor} 於批次交易提交後執行
 * (stix_objects 的 FK 指向 indicators,且投影失敗不得使 ingestion 失敗——§7.8.6;ADR 0005)。
 * 任何投影錯誤只記錄並繼續,絕不 reject 該筆。
 *
 * <p>M2 起除 {@code indicator} 外另產生兩種物件(§7.8.1、§7.8.7):
 * 每個來源記錄一筆 {@code observed-data}(單一來源的一次觀測)、每個涉及來源一筆 {@code identity}
 * (情資提供方)。三者的 id 都是決定性的,重投影一律是 UPSERT。
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
            Map<SourceId, SourceSnapshot> involved = involvedSources(snapshot);
            context.addStixProjection(StixIndicatorProjector.project(
                    snapshot, displayNames(involved), createdOf(StixIndicatorProjector.stixId(snapshot), now), now));
            for (IndicatorSourceSnapshot record : snapshot.sources()) {
                String observedDataId = StixObservedDataProjector.stixId(snapshot, record);
                context.addStixProjection(
                        StixObservedDataProjector.project(snapshot, record, createdOf(observedDataId, now), now));
            }
            for (SourceSnapshot source : involved.values()) {
                context.addStixProjection(StixIdentityProjector.project(
                        source, createdOf(StixIdentityProjector.stixId(source), now), now));
            }
        } catch (RuntimeException e) {
            context.clearStixProjections();
            log.warn("STIX 投影建構失敗,只記錄不影響 ingestion(§7.8.6):{}", context.normalizedValue(), e);
        }
        return context;
    }

    /** 既有投影的 STIX created 保持穩定(ADR 0005);第一次投影以當下時間為 created。 */
    private Instant createdOf(String stixId, Instant now) {
        return stixObjects.findCreated(stixId).orElse(now);
    }

    private Map<SourceId, SourceSnapshot> involvedSources(IndicatorSnapshot snapshot) {
        Map<SourceId, SourceSnapshot> found = new HashMap<>();
        for (IndicatorSourceSnapshot record : snapshot.sources()) {
            sources.findById(record.sourceId())
                    .map(Source::snapshot)
                    .ifPresent(source -> found.put(record.sourceId(), source));
        }
        return found;
    }

    private static Map<SourceId, String> displayNames(Map<SourceId, SourceSnapshot> sources) {
        Map<SourceId, String> names = new HashMap<>();
        sources.forEach((id, snapshot) -> names.put(id, snapshot.displayName()));
        return names;
    }
}
