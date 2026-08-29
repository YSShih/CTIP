package com.ctip.interfaces.rest;

import com.ctip.application.threat.LinkedIndicator;
import com.ctip.domain.shared.CursorPage;
import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.threat.Threat;
import com.ctip.interfaces.rest.dto.common.PageResponse;
import com.ctip.interfaces.rest.dto.threat.ThreatDto;
import com.ctip.interfaces.rest.dto.threat.ThreatIndicatorDto;
import com.ctip.interfaces.rest.mapper.ThreatDtoMapper;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Threat 回應組裝:DTO 映射 + cursor 包裝 + 關聯 IOC 的再散布遮罩(後者委派
 * {@link IocResponseAssembler},政策判斷仍集中在 {@code RedistributionFilter} 單點)。
 */
@Component
public class ThreatResponseAssembler {

    private final ThreatDtoMapper mapper;
    private final IocResponseAssembler iocs;
    private final CursorCodec cursorCodec;

    ThreatResponseAssembler(ThreatDtoMapper mapper, IocResponseAssembler iocs, CursorCodec cursorCodec) {
        this.mapper = mapper;
        this.iocs = iocs;
        this.cursorCodec = cursorCodec;
    }

    public ThreatDto toDto(Threat threat) {
        return mapper.toDto(threat);
    }

    public PageResponse<ThreatDto> toPage(CursorPage<Threat> page) {
        return new PageResponse<>(
                page.items().stream().map(mapper::toDto).toList(),
                cursorCodec.wrapInternal(page.nextCursor()),
                page.hasMore());
    }

    /** 關聯清單:IOC 部分沿用 IOC 的 DTO 與遮罩規則,兩處不得各寫一套。 */
    public List<ThreatIndicatorDto> toLinkDtos(List<LinkedIndicator> links, TenantId viewer) {
        return links.stream()
                .map(link -> new ThreatIndicatorDto(
                        link.role().name(), link.addedAt(), iocs.toDto(link.indicator(), viewer)))
                .toList();
    }
}
