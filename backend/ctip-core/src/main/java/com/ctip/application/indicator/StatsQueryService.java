package com.ctip.application.indicator;

import com.ctip.application.port.ClockPort;
import com.ctip.application.port.StatsPort;
import com.ctip.domain.shared.Visibility;
import java.util.List;
import org.springframework.stereotype.Service;

/** Dashboard 統計(docs/spec/09-api.md §9.1 /stats/*):彙整經統一可見度過濾的公開統計。 */
@Service
public class StatsQueryService {

    private final StatsPort stats;
    private final ClockPort clock;

    public StatsQueryService(StatsPort stats, ClockPort clock) {
        this.stats = stats;
        this.clock = clock;
    }

    public StatsPort.StatsSummary summary(Visibility visibility) {
        return stats.summary(visibility, clock.now());
    }

    public List<StatsPort.SourceStats> sources(Visibility visibility) {
        return stats.sources(visibility);
    }
}
