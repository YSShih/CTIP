package com.ctip.domain.indicator;

import com.ctip.domain.source.Reputation;
import com.ctip.domain.source.SourceId;
import com.ctip.sdk.Confidence;
import com.ctip.sdk.IocType;
import com.ctip.sdk.Severity;
import com.ctip.sdk.Tlp;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * I5–I11 的聚合規則,無狀態純函式(docs/spec/07-domain-intel.md §7.5)。
 * 需要 source.reputation 時以參數傳入——domain 物件不得持有 repository。
 */
public final class IndicatorMergePolicy {

    private static final int NEUTRAL_CONFIDENCE = 50;
    private static final int MULTI_SOURCE_BONUS = 10;
    private static final int MULTI_SOURCE_THRESHOLD = 3;

    private IndicatorMergePolicy() {}

    /** I5:firstSeen = MIN(sourceFirstSeen)。 */
    public static Instant aggregateFirstSeen(List<IndicatorSource> records) {
        return records.stream()
                .map(IndicatorSource::sourceFirstSeen)
                .min(Instant::compareTo)
                .orElseThrow();
    }

    /** I5:lastSeen = MAX(sourceLastSeen)。 */
    public static Instant aggregateLastSeen(List<IndicatorSource> records) {
        return records.stream()
                .map(IndicatorSource::sourceLastSeen)
                .max(Instant::compareTo)
                .orElseThrow();
    }

    /** I6:validUntil = MAX(effectiveValidUntil);僅當所有來源皆為 null 時為 null。 */
    public static Instant aggregateValidUntil(List<IndicatorSource> records, IocType type) {
        return records.stream()
                .map(r -> r.effectiveValidUntil(type))
                .filter(java.util.Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(null);
    }

    /**
     * I10:依 reputation 加權平均(只計 ACTIVE 且 confidence 非 null 的來源;
     * 全部未提供則取中性值 50),獨立 ACTIVE 來源數 >= 3 時 +10,上限 100。
     * 缺席的 reputation 以中性值 50 計。
     */
    public static Confidence aggregateConfidence(List<IndicatorSource> records, Map<SourceId, Reputation> reputations) {
        long weightedSum = 0;
        long weightTotal = 0;
        for (IndicatorSource r : records) {
            if (r.status() != SourceRecordStatus.ACTIVE || r.sourceConfidence() == null) {
                continue;
            }
            int reputation = reputations
                    .getOrDefault(r.sourceId(), new Reputation(NEUTRAL_CONFIDENCE))
                    .value();
            weightedSum += (long) r.sourceConfidence().value() * reputation;
            weightTotal += reputation;
        }
        long weighted = weightTotal == 0 ? NEUTRAL_CONFIDENCE : Math.round((double) weightedSum / weightTotal);
        int bonus = activeSourceCount(records) >= MULTI_SOURCE_THRESHOLD ? MULTI_SOURCE_BONUS : 0;
        return Confidence.of((int) Math.min(100, weighted + bonus));
    }

    /** I8:severity = MAX(sourceSeverity);全部未提供時為 INFO。 */
    public static Severity aggregateSeverity(List<IndicatorSource> records) {
        return records.stream()
                .map(IndicatorSource::sourceSeverity)
                .filter(java.util.Objects::nonNull)
                .reduce(Severity.INFO, Severity::max);
    }

    /** I7:tlp = 所有來源中最嚴格者。 */
    public static Tlp strictestTlp(List<IndicatorSource> records) {
        return records.stream().map(IndicatorSource::sourceTlp).reduce(Tlp.CLEAR, Tlp::strictest);
    }

    /** I9:tags = 所有來源 tags 的聯集。 */
    public static Set<String> unionTags(List<IndicatorSource> records) {
        Set<String> union = new HashSet<>();
        records.forEach(r -> union.addAll(r.tags()));
        return union;
    }

    /** 獨立 ACTIVE 來源數(sourceCount 快取值的來源)。 */
    public static int activeSourceCount(List<IndicatorSource> records) {
        return (int) records.stream()
                .filter(r -> r.status() == SourceRecordStatus.ACTIVE)
                .count();
    }

    /** I11:status 判定順序,強制短路求值(docs/spec/07-domain-intel.md §7.5)。 */
    public static IndicatorStatus determineStatus(
            List<IndicatorSource> records, Map<SourceId, Reputation> reputations) {
        boolean trustedRetraction = records.stream()
                .anyMatch(r -> r.status() == SourceRecordStatus.RETRACTED
                        && reputations
                                .getOrDefault(r.sourceId(), new Reputation(NEUTRAL_CONFIDENCE))
                                .isTrustedForRetraction());
        if (trustedRetraction) {
            return IndicatorStatus.REVOKED;
        }
        boolean anyFalsePositive = records.stream().anyMatch(r -> r.status() == SourceRecordStatus.FALSE_POSITIVE);
        if (anyFalsePositive && activeSourceCount(records) == 0) {
            return IndicatorStatus.FALSE_POSITIVE;
        }
        boolean allExpired = records.stream().allMatch(r -> r.status() == SourceRecordStatus.EXPIRED);
        if (allExpired) {
            return IndicatorStatus.EXPIRED;
        }
        return IndicatorStatus.ACTIVE;
    }
}
