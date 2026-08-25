package com.ctip.domain.indicator;

import com.ctip.domain.source.Reputation;
import com.ctip.domain.source.SourceId;
import com.ctip.sdk.Severity;
import java.time.Duration;
import java.time.InstantSource;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * M1 規則式評分(docs/spec/07-domain-intel.md §7.6):
 * confidence 40% + severity 25% + 獨立 ACTIVE 來源數 20%(log 尺度,上限 5)+ recency 15%
 * (半衰期 30 天)。來源數定義與 confidence 的多來源加成一致(皆為獨立 ACTIVE 來源數)。
 * 時間經 {@link InstantSource} 注入(ClockPort 以 method reference 適配),domain 不呼叫 Instant.now()。
 */
public final class RuleBasedThreatScorer implements ThreatScorer {

    private static final double SOURCE_CAP_LOG = Math.log(6);
    private static final double HALF_LIFE_DAYS = 30.0;
    private static final double SECONDS_PER_DAY = 86_400.0;

    private final InstantSource clock;

    public RuleBasedThreatScorer(InstantSource clock) {
        this.clock = Objects.requireNonNull(clock, "clock 不得為 null");
    }

    @Override
    public int score(Indicator indicator, List<IndicatorSource> sources, Map<SourceId, Reputation> reputations) {
        double confidencePart = indicator.confidence().value() / 100.0 * 40;
        double severityPart = severityValue(indicator.severity()) * 0.25;
        int activeSources = IndicatorMergePolicy.activeSourceCount(sources);
        double sourcesPart = Math.min(1.0, Math.log(1.0 + activeSources) / SOURCE_CAP_LOG) * 20;
        double recencyPart = Math.pow(0.5, daysSinceLastSeen(indicator) / HALF_LIFE_DAYS) * 15;
        int score = (int) Math.round(confidencePart + severityPart + sourcesPart + recencyPart);
        return Math.max(0, Math.min(100, score));
    }

    private double daysSinceLastSeen(Indicator indicator) {
        Duration since = Duration.between(indicator.snapshot().lastSeen(), clock.instant());
        return Math.max(0, since.toSeconds()) / SECONDS_PER_DAY;
    }

    private static int severityValue(Severity severity) {
        return switch (severity) {
            case INFO -> 0;
            case LOW -> 25;
            case MEDIUM -> 50;
            case HIGH -> 75;
            case CRITICAL -> 100;
        };
    }
}
