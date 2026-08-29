package com.ctip.interfaces.rest;

import com.ctip.application.threat.ThreatFilter;
import com.ctip.application.threat.ThreatQueryService;
import com.ctip.domain.shared.Cursor;
import com.ctip.domain.threat.Threat;
import com.ctip.domain.threat.ThreatId;
import com.ctip.infrastructure.security.TenantContext;
import com.ctip.interfaces.rest.dto.common.PageResponse;
import com.ctip.interfaces.rest.dto.threat.ThreatDto;
import com.ctip.interfaces.rest.dto.threat.ThreatIndicatorDto;
import com.ctip.interfaces.rest.dto.threat.ThreatListParams;
import com.ctip.interfaces.rest.error.ApiException;
import com.ctip.interfaces.rest.openapi.ThreatApi;
import java.util.List;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Threat 讀取端點(docs/spec/09-api.md §9.1「Threat」,皆匿名可用)。寫入端點在
 * {@link ThreatWriteController}。
 *
 * <p>業務規則不在 controller(規則 10):可見度過濾在 Specification 層、關聯 IOC 的第二層
 * 可見度在 {@code ThreatQueryService}、再散布遮罩在 {@link ThreatResponseAssembler}。
 */
@RestController
@RequestMapping("/api/v1/threats")
class ThreatController implements ThreatApi {

    private final ThreatQueryService query;
    private final TenantContext tenantContext;
    private final ThreatResponseAssembler assembler;
    private final CursorCodec cursorCodec;
    private final ReadQuotaPolicy readQuotas;

    ThreatController(
            ThreatQueryService query,
            TenantContext tenantContext,
            ThreatResponseAssembler assembler,
            CursorCodec cursorCodec,
            ReadQuotaPolicy readQuotas) {
        this.query = query;
        this.tenantContext = tenantContext;
        this.assembler = assembler;
        this.cursorCodec = cursorCodec;
        this.readQuotas = readQuotas;
    }

    @Override
    @GetMapping
    @PreAuthorize("hasAuthority('threat:read')")
    public PageResponse<ThreatDto> list(@ParameterObject ThreatListParams params) {
        ThreatFilter filter = new ThreatFilter(
                params.type(),
                params.status(),
                params.severity(),
                params.tlp(),
                params.includeRetiredOrDefault(),
                params.name(),
                params.tags(),
                params.aliases());
        Cursor after = cursorCodec.decode(params.cursor());
        int pageSize = readQuotas.clampPageSize(tenantContext.tenantId(), params.limit());
        return assembler.toPage(query.list(filter, tenantContext.visibility(), after, pageSize));
    }

    @Override
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('threat:read')")
    public ThreatDto byId(@PathVariable UUID id) {
        return assembler.toDto(visibleThreat(id));
    }

    /** 關聯的 IOC;viewer 看不到的 IOC 不出現(關聯不是可見度的旁路)。 */
    @Override
    @GetMapping("/{id}/indicators")
    @PreAuthorize("hasAuthority('threat:read')")
    public List<ThreatIndicatorDto> indicators(@PathVariable UUID id) {
        return assembler.toLinkDtos(
                query.linkedIndicators(visibleThreat(id), tenantContext.visibility()), tenantContext.tenantId());
    }

    /** 不存在與跨租戶不可見一律 404(§9.4),避免洩漏資源存在性。 */
    private Threat visibleThreat(UUID id) {
        return query.byId(new ThreatId(id), tenantContext.visibility()).orElseThrow(ApiException::notFound);
    }
}
