package com.ctip.application.stix;

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
import com.ctip.domain.stix.StixProjection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 一個 indicator 的完整 STIX 投影組(§7.8.1、§7.8.7):{@code indicator} 一筆、
 * 每個來源記錄一筆 {@code observed-data}、每個涉及來源一筆 {@code identity}。
 *
 * <p>抽出來是因為它有<strong>兩個</strong>呼叫端:ingestion 的 {@code StixProjectionStage}
 * (stage 8)與管理端點的 {@link StixRebuildService}。重建若各自算一次,
 * 兩條路徑產出的 id 或 created 一旦漂移,重建就會製造出重複的 STIX 物件。
 */
@Service
public class StixProjectionFactory {

    private final SourceRepository sources;
    private final StixObjectPort stixObjects;
    private final ClockPort clock;

    public StixProjectionFactory(SourceRepository sources, StixObjectPort stixObjects, ClockPort clock) {
        this.sources = sources;
        this.stixObjects = stixObjects;
        this.clock = clock;
    }

    public List<StixProjection> projectionsFor(IndicatorSnapshot snapshot) {
        Instant now = clock.now();
        Map<SourceId, SourceSnapshot> involved = involvedSources(snapshot);
        List<StixProjection> projections = new ArrayList<>();
        projections.add(StixIndicatorProjector.project(
                snapshot, displayNames(involved), createdOf(StixIndicatorProjector.stixId(snapshot), now), now));
        for (IndicatorSourceSnapshot record : snapshot.sources()) {
            String observedDataId = StixObservedDataProjector.stixId(snapshot, record);
            projections.add(StixObservedDataProjector.project(snapshot, record, createdOf(observedDataId, now), now));
        }
        for (SourceSnapshot source : involved.values()) {
            projections.add(
                    StixIdentityProjector.project(source, createdOf(StixIdentityProjector.stixId(source), now), now));
        }
        return projections;
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
