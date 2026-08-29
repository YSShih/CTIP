package com.ctip.interfaces.rest;

import com.ctip.application.identity.AuthenticatedIdentity;
import com.ctip.application.threat.CreateThreatCommand;
import com.ctip.application.threat.ThreatService;
import com.ctip.domain.indicator.IndicatorId;
import com.ctip.domain.threat.ExternalReference;
import com.ctip.domain.threat.IndicatorRole;
import com.ctip.domain.threat.ThreatId;
import com.ctip.infrastructure.security.TenantContext;
import com.ctip.interfaces.rest.dto.threat.ExternalReferenceRequest;
import com.ctip.interfaces.rest.dto.threat.ThreatCreateRequest;
import com.ctip.interfaces.rest.dto.threat.ThreatDto;
import com.ctip.interfaces.rest.dto.threat.ThreatLinkRequest;
import com.ctip.interfaces.rest.dto.threat.ThreatStatusRequest;
import com.ctip.interfaces.rest.error.ApiException;
import com.ctip.interfaces.rest.openapi.ThreatWriteApi;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Threat 寫入端點(docs/spec/09-api.md §9.1「Threat — 寫入」;權限 {@code threat:manage})。
 *
 * <p><strong>本版新增(ADR 0027)。</strong> §9.1 原本只有三個 GET,而 ingestion 不產生 Threat、
 * Phase 19–23 也沒有任何建立管道——照原樣實作,{@code threats} 三張表與
 * {@code Threat.linkIndicator}/{@code changeStatus} 在正式環境永遠不可達,正是規則 16 禁止的
 * placeholder。這與 v2.0 為 {@code FALSE_POSITIVE} 補寫入端點的處置是同一件事。
 *
 * <p>與讀取端點分開的 controller:讀取全部匿名可用,寫入每一個都要權限,前置條件沒有交集。
 * 業務規則不在此(規則 10)——歸屬、H1/H4 的衝突判定、H6 的 TLP 收緊都在 {@link ThreatService}。
 */
@RestController
@RequestMapping("/api/v1/threats")
class ThreatWriteController implements ThreatWriteApi {

    private final ThreatService threats;
    private final ThreatResponseAssembler assembler;
    private final TenantContext tenantContext;

    ThreatWriteController(ThreatService threats, ThreatResponseAssembler assembler, TenantContext tenantContext) {
        this.threats = threats;
        this.assembler = assembler;
        this.tenantContext = tenantContext;
    }

    @Override
    @PostMapping
    @PreAuthorize("hasAuthority('threat:manage')")
    public ResponseEntity<ThreatDto> create(@Valid @RequestBody ThreatCreateRequest request) {
        AuthenticatedIdentity actor = tenantContext.requireIdentity();
        CreateThreatCommand command = new CreateThreatCommand(
                request.type(),
                request.name(),
                request.aliases(),
                request.description(),
                request.severity(),
                request.confidence(),
                request.tlp(),
                request.tags(),
                request.firstSeen(),
                request.lastSeen());
        return ResponseEntity.status(HttpStatus.CREATED).body(assembler.toDto(threats.create(command, actor)));
    }

    @Override
    @PutMapping("/{id}/indicators/{indicatorId}")
    @PreAuthorize("hasAuthority('threat:manage')")
    public ThreatDto linkIndicator(
            @PathVariable UUID id,
            @PathVariable UUID indicatorId,
            @RequestBody(required = false) ThreatLinkRequest request) {
        IndicatorRole role = request == null || request.role() == null ? IndicatorRole.UNKNOWN : request.role();
        return threats.linkIndicator(
                        new ThreatId(id), new IndicatorId(indicatorId), role, tenantContext.requireIdentity())
                .map(assembler::toDto)
                .orElseThrow(ApiException::notFound);
    }

    @Override
    @DeleteMapping("/{id}/indicators/{indicatorId}")
    @PreAuthorize("hasAuthority('threat:manage')")
    public ThreatDto unlinkIndicator(@PathVariable UUID id, @PathVariable UUID indicatorId) {
        return threats.unlinkIndicator(new ThreatId(id), new IndicatorId(indicatorId), tenantContext.requireIdentity())
                .map(assembler::toDto)
                .orElseThrow(ApiException::notFound);
    }

    @Override
    @PostMapping("/{id}/external-references")
    @PreAuthorize("hasAuthority('threat:manage')")
    public ResponseEntity<ThreatDto> addExternalReference(
            @PathVariable UUID id, @Valid @RequestBody ExternalReferenceRequest request) {
        ExternalReference reference =
                new ExternalReference(request.sourceName(), request.externalId(), request.url(), request.description());
        ThreatDto dto = threats.addExternalReference(new ThreatId(id), reference, tenantContext.requireIdentity())
                .map(assembler::toDto)
                .orElseThrow(ApiException::notFound);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @Override
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('threat:manage')")
    public ThreatDto changeStatus(@PathVariable UUID id, @Valid @RequestBody ThreatStatusRequest request) {
        return threats.changeStatus(new ThreatId(id), request.status(), tenantContext.requireIdentity())
                .map(assembler::toDto)
                .orElseThrow(ApiException::notFound);
    }
}
