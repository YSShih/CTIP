package com.ctip.interfaces.rest;

import com.ctip.application.indicator.StatsQueryService;
import com.ctip.application.port.StatsPort;
import com.ctip.infrastructure.security.TenantContext;
import com.ctip.interfaces.rest.dto.stats.SourceStatsDto;
import com.ctip.interfaces.rest.dto.stats.StatsSummaryDto;
import com.ctip.interfaces.rest.mapper.SourceDtoMapper;
import com.ctip.interfaces.rest.openapi.StatsApi;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dashboard 統計端點(docs/spec/09-api.md §9.1;統計口徑經統一可見度過濾)。
 *
 * <p>{@code stats:read} 是 ANONYMOUS 起全部角色都持有的權限,匿名仍可存取;
 * 標註的作用是讓 scope 受限的 API key 也受同一套授權約束(ADR 0013)。
 */
@RestController
@RequestMapping("/api/v1/stats")
class StatsController implements StatsApi {

    private final StatsQueryService stats;
    private final TenantContext tenantContext;
    private final SourceDtoMapper sourceMapper;

    StatsController(StatsQueryService stats, TenantContext tenantContext, SourceDtoMapper sourceMapper) {
        this.stats = stats;
        this.tenantContext = tenantContext;
        this.sourceMapper = sourceMapper;
    }

    @Override
    @PreAuthorize("hasAuthority('stats:read')")
    @GetMapping("/summary")
    public StatsSummaryDto summary() {
        StatsPort.StatsSummary summary = stats.summary(tenantContext.visibility());
        return new StatsSummaryDto(
                summary.totalActive(),
                summary.byType(),
                summary.last7Days().stream()
                        .map(d -> new StatsSummaryDto.DailyCountDto(d.date(), d.count()))
                        .toList());
    }

    @Override
    @PreAuthorize("hasAuthority('stats:read')")
    @GetMapping("/sources")
    public List<SourceStatsDto> sources() {
        return stats.sources(tenantContext.visibility()).stream()
                .map(sourceMapper::toStatsDto)
                .toList();
    }
}
