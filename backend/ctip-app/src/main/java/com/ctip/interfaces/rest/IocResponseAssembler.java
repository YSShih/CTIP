package com.ctip.interfaces.rest;

import com.ctip.application.indicator.RedistributionFilter;
import com.ctip.application.source.SourceQueryService;
import com.ctip.domain.indicator.Indicator;
import com.ctip.domain.indicator.IndicatorSourceSnapshot;
import com.ctip.domain.shared.CursorPage;
import com.ctip.domain.source.Source;
import com.ctip.domain.source.SourceId;
import com.ctip.domain.source.SourceSnapshot;
import com.ctip.domain.tenant.TenantId;
import com.ctip.interfaces.rest.dto.common.PageResponse;
import com.ctip.interfaces.rest.dto.ioc.AttributionDto;
import com.ctip.interfaces.rest.dto.ioc.IocDto;
import com.ctip.interfaces.rest.dto.ioc.IocSourceDto;
import com.ctip.interfaces.rest.mapper.IocDtoMapper;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * IOC 回應組裝(docs/spec/09-api.md §9.5 輸出過濾第 4–5 步):
 * 再散布政策的遮罩與標註一律經 {@link RedistributionFilter} 判定(單點,不散落 controller),
 * 之後才做 DTO 映射。來源名稱一次查表(M1 來源固定 4 筆)。
 */
@Component
public class IocResponseAssembler {

    private final RedistributionFilter redistribution;
    private final SourceQueryService sources;
    private final IocDtoMapper mapper;
    private final CursorCodec cursorCodec;

    IocResponseAssembler(
            RedistributionFilter redistribution,
            SourceQueryService sources,
            IocDtoMapper mapper,
            CursorCodec cursorCodec) {
        this.redistribution = redistribution;
        this.sources = sources;
        this.mapper = mapper;
        this.cursorCodec = cursorCodec;
    }

    public IocDto toDto(Indicator indicator, TenantId viewer) {
        return toDto(indicator, viewer, sourceSnapshots());
    }

    public PageResponse<IocDto> toPage(CursorPage<Indicator> page, TenantId viewer) {
        Map<SourceId, SourceSnapshot> lookup = sourceSnapshots();
        List<IocDto> items =
                page.items().stream().map(i -> toDto(i, viewer, lookup)).toList();
        return new PageResponse<>(items, cursorCodec.wrapInternal(page.nextCursor()), page.hasMore());
    }

    public PageResponse<IocDto> toPage(List<Indicator> items, boolean hasMore, TenantId viewer) {
        Map<SourceId, SourceSnapshot> lookup = sourceSnapshots();
        return new PageResponse<>(
                items.stream().map(i -> toDto(i, viewer, lookup)).toList(), null, hasMore);
    }

    /** 來源明細(規則 5):跨租戶僅含可揭露政策的來源記錄。 */
    public List<IocSourceDto> toSourceDtos(Indicator indicator, TenantId viewer) {
        Map<SourceId, SourceSnapshot> lookup = sourceSnapshots();
        return redistribution.visibleSourceRecords(indicator, viewer).stream()
                .map(record -> mapper.toSourceDto(record, displayName(lookup, record.sourceId())))
                .toList();
    }

    private IocDto toDto(Indicator indicator, TenantId viewer, Map<SourceId, SourceSnapshot> lookup) {
        List<AttributionDto> attribution = redistribution.attributionRequired(indicator, viewer).stream()
                .map(IndicatorSourceSnapshot::sourceId)
                .distinct()
                .map(lookup::get)
                .filter(java.util.Objects::nonNull)
                .map(s -> new AttributionDto(s.displayName(), s.homepageUrl()))
                .toList();
        return mapper.toDto(indicator, attribution);
    }

    private Map<SourceId, SourceSnapshot> sourceSnapshots() {
        return sources.all().stream()
                .map(Source::snapshot)
                .collect(Collectors.toMap(SourceSnapshot::id, Function.identity()));
    }

    private static String displayName(Map<SourceId, SourceSnapshot> lookup, SourceId id) {
        SourceSnapshot snapshot = lookup.get(id);
        return snapshot == null ? id.value().toString() : snapshot.displayName();
    }
}
