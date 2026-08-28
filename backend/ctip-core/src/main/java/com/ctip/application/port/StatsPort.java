package com.ctip.application.port;

import com.ctip.domain.shared.Visibility;
import com.ctip.domain.source.SourceId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Dashboard 公開統計(docs/spec/09-api.md §9.1 /stats/*)。
 * <strong>兩個查詢都經統一可見度過濾</strong>(與 findVisible 同一套規則)。
 * {@code sources} 的 {@code sources} 表本身無租戶歸屬,但 {@code indicatorCount} 是
 * indicator 的計數——不過濾就會把租戶私有情資的提交量即時公開(ADR 0015)。
 */
public interface StatsPort {

    StatsSummary summary(Visibility visibility, Instant now);

    List<SourceStats> sources(Visibility visibility);

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
