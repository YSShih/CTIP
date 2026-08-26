package com.ctip.interfaces.rest.mapper;

import com.ctip.domain.indicator.Indicator;
import com.ctip.domain.indicator.IndicatorSnapshot;
import com.ctip.domain.indicator.IndicatorSourceSnapshot;
import com.ctip.interfaces.rest.dto.ioc.AttributionDto;
import com.ctip.interfaces.rest.dto.ioc.IocDto;
import com.ctip.interfaces.rest.dto.ioc.IocSourceDto;
import java.util.List;
import org.mapstruct.Mapper;

/**
 * Indicator domain → 回應 DTO(docs/spec/09-api.md §9.5)。
 * 輸出過濾第 4 步(再散布遮罩)由呼叫端以 RedistributionFilter 決定可見來源與標註後傳入;
 * 本 mapper 只做資料承載映射,不含政策判斷。
 */
@Mapper(componentModel = "spring")
public interface IocDtoMapper {

    default IocDto toDto(Indicator indicator, List<AttributionDto> attribution) {
        IndicatorSnapshot s = indicator.snapshot();
        return new IocDto(
                s.id().value(),
                s.value().type().name(),
                s.value().hashType() == null ? null : s.value().hashType().name(),
                s.value().normalized(),
                s.confidence().value(),
                s.severity().name(),
                s.score(),
                s.tlp().name(),
                s.status().name(),
                s.firstSeen(),
                s.lastSeen(),
                s.validUntil(),
                (int) s.sources().stream()
                        .filter(r -> r.status() == com.ctip.domain.indicator.SourceRecordStatus.ACTIVE)
                        .count(),
                s.tags(),
                attribution);
    }

    default IocSourceDto toSourceDto(IndicatorSourceSnapshot record, String sourceName) {
        return new IocSourceDto(
                record.sourceId().value(),
                sourceName,
                record.sourceConfidence() == null
                        ? null
                        : record.sourceConfidence().value(),
                record.sourceSeverity() == null ? null : record.sourceSeverity().name(),
                record.sourceTlp().name(),
                record.sourceFirstSeen(),
                record.sourceLastSeen(),
                record.sourceValidUntil(),
                record.reportCount(),
                record.status().name(),
                record.redistributionPolicy().name());
    }
}
