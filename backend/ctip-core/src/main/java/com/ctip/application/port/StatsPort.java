package com.ctip.application.port;

import com.ctip.domain.shared.Visibility;
import com.ctip.domain.source.SourceId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Dashboard 公開統計(docs/spec/09-api.md §9.1 /stats/*)。
 * summary 一律經統一可見度過濾(與 findVisible 同一套規則);
 * sources 為來源健康與筆數概況(sources 表本身無租戶歸屬)。
 */
public interface StatsPort {

    StatsSummary summary(Visibility visibility, Instant now);

    List<SourceStats> sources();

    /** 總數為可見的未過期 ACTIVE;trend 為近 7 日(依 lastSeen 日期)每日筆數,含 0 的日期。 */
    record StatsSummary(long totalActive, Map<String, Long> byType, List<DailyCount> last7Days) {}

    record DailyCount(LocalDate date, long count) {}

    record SourceStats(
            SourceId sourceId,
            String sourceType,
            String displayName,
            String status,
            boolean enabled,
            long indicatorCount,
            Instant lastSuccessAt) {}
}
