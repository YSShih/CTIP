package com.ctip.interfaces.rest.dto.stats;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 公開統計(GET /stats/summary):可見的 ACTIVE 總數、型別分布、近 7 日趨勢。 */
public record StatsSummaryDto(long totalActive, Map<String, Long> byType, List<DailyCountDto> trend) {

    public record DailyCountDto(LocalDate date, long count) {}
}
