package com.ctip.domain.indicator;

import com.ctip.domain.source.Reputation;
import com.ctip.domain.source.SourceId;
import java.util.List;
import java.util.Map;

/**
 * 威脅評分(docs/spec/07-domain-intel.md §7.6)。保持在抽象之後,未來可換 ML 模型;
 * M1 只有 {@link RuleBasedThreatScorer},不得實作任何 ML。
 */
public interface ThreatScorer {

    int score(Indicator indicator, List<IndicatorSource> sources, Map<SourceId, Reputation> reputations);
}
