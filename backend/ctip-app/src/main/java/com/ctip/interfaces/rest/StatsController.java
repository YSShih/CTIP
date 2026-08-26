package com.ctip.interfaces.rest;

import com.ctip.application.indicator.StatsQueryService;
import com.ctip.application.port.StatsPort;
import com.ctip.infrastructure.security.TenantContext;
import com.ctip.interfaces.rest.dto.stats.SourceStatsDto;
import com.ctip.interfaces.rest.dto.stats.StatsSummaryDto;
import com.ctip.interfaces.rest.mapper.SourceDtoMapper;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Dashboard 統計端點(docs/spec/09-api.md §9.1,匿名;統計口徑經統一可見度過濾)。 */
@RestController
@RequestMapping("/api/v1/stats")
class StatsController {

    private final StatsQueryService stats;
    private final TenantContext tenantContext;
    private final SourceDtoMapper sourceMapper;

    StatsController(StatsQueryService stats, TenantContext tenantContext, SourceDtoMapper sourceMapper) {
        this.stats = stats;
        this.tenantContext = tenantContext;
        this.sourceMapper = sourceMapper;
    }

    @GetMapping("/summary")
    StatsSummaryDto summary() {
        StatsPort.StatsSummary summary = stats.summary(tenantContext.visibility());
        return new StatsSummaryDto(
                summary.totalActive(),
                summary.byType(),
                summary.last7Days().stream()
                        .map(d -> new StatsSummaryDto.DailyCountDto(d.date(), d.count()))
                        .toList());
    }

    @GetMapping("/sources")
    List<SourceStatsDto> sources() {
        return stats.sources().stream().map(sourceMapper::toStatsDto).toList();
    }
}
