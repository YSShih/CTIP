package com.ctip.application.ingestion;

import com.ctip.domain.indicator.ThreatScorer;

/** Stage 7 Score:規則式評分(§7.6);來源與信譽由聚合自身提供,保持在 ThreatScorer 抽象之後。 */
public final class ScoreStage implements IngestionStage {

    private final ThreatScorer scorer;

    public ScoreStage(ThreatScorer scorer) {
        this.scorer = scorer;
    }

    @Override
    public String name() {
        return "Score";
    }

    @Override
    public IngestionContext execute(IngestionContext context) {
        context.indicator().applyScore(scorer);
        return context;
    }
}
