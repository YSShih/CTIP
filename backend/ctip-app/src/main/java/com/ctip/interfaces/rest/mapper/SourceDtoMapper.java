package com.ctip.interfaces.rest.mapper;

import com.ctip.application.port.StatsPort;
import com.ctip.domain.source.Source;
import com.ctip.domain.source.SourceSnapshot;
import com.ctip.interfaces.rest.dto.source.SourceDto;
import com.ctip.interfaces.rest.dto.source.SourceStatusDto;
import com.ctip.interfaces.rest.dto.stats.SourceStatsDto;
import org.mapstruct.Mapper;

/** Source domain / 統計查詢結果 → 回應 DTO(docs/spec/09-api.md §9.5)。 */
@Mapper(componentModel = "spring")
public interface SourceDtoMapper {

    default SourceDto toDto(Source source) {
        SourceSnapshot s = source.snapshot();
        return new SourceDto(
                s.id().value(),
                s.sourceType().name(),
                s.displayName(),
                s.homepageUrl(),
                s.defaultTlp().name(),
                s.redistributionPolicy().name(),
                s.reputation().value(),
                s.enabled(),
                s.syncable(),
                s.health().status().name(),
                s.totalRecordsIngested());
    }

    default SourceStatusDto toStatusDto(Source source) {
        SourceSnapshot s = source.snapshot();
        return new SourceStatusDto(
                s.id().value(),
                s.health().status().name(),
                s.health().consecutiveFailures(),
                s.health().lastSyncAt(),
                s.health().lastSuccessAt(),
                s.health().lastFailureAt(),
                s.lastErrorMessage(),
                s.health().avgLatencyMs());
    }

    default SourceStatsDto toStatsDto(StatsPort.SourceStats stats) {
        return new SourceStatsDto(
                stats.sourceId().value(),
                stats.sourceType(),
                stats.displayName(),
                stats.status(),
                stats.enabled(),
                stats.indicatorCount(),
                stats.lastSuccessAt());
    }
}
