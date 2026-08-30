package com.ctip.interfaces.rest;

import com.ctip.application.audit.AuditLogQuery;
import com.ctip.application.audit.AuditQueryService;
import com.ctip.application.audit.AuditRecord;
import com.ctip.application.identity.AuthenticatedIdentity;
import com.ctip.domain.audit.AuditAction;
import com.ctip.domain.shared.CursorPage;
import com.ctip.infrastructure.security.TenantContext;
import com.ctip.interfaces.rest.dto.audit.AuditLogDto;
import com.ctip.interfaces.rest.dto.common.PageResponse;
import com.ctip.interfaces.rest.error.ApiException;
import com.ctip.interfaces.rest.error.ErrorCode;
import com.ctip.interfaces.rest.mapper.AuditDtoMapper;
import com.ctip.interfaces.rest.openapi.AuditLogApi;
import java.util.Locale;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 稽核軌跡端點(docs/spec/09-api.md §9.1「通知與稽核」)。
 * 範圍固定為呼叫者自己的租戶——沒有「看別的租戶的稽核軌跡」這件事。
 */
@RestController
@RequestMapping("/api/v1/audit-logs")
class AuditLogController implements AuditLogApi {

    private final AuditQueryService auditLogs;
    private final ReadQuotaPolicy readQuota;
    private final TenantContext tenantContext;
    private final AuditDtoMapper mapper;
    private final CursorCodec cursors;

    AuditLogController(
            AuditQueryService auditLogs,
            ReadQuotaPolicy readQuota,
            TenantContext tenantContext,
            AuditDtoMapper mapper,
            CursorCodec cursors) {
        this.auditLogs = auditLogs;
        this.readQuota = readQuota;
        this.tenantContext = tenantContext;
        this.mapper = mapper;
        this.cursors = cursors;
    }

    @Override
    @GetMapping
    @PreAuthorize("hasAuthority('audit:read')")
    public PageResponse<AuditLogDto> listAuditLogs(
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String action) {
        AuthenticatedIdentity caller = tenantContext.requireIdentity();
        CursorPage<AuditRecord> page = auditLogs.list(new AuditLogQuery(
                caller.tenantId(),
                parseAction(action),
                cursors.decode(cursor),
                readQuota.clampPageSize(caller.tenantId(), limit)));
        return new PageResponse<>(
                page.items().stream().map(mapper::toDto).toList(),
                cursors.wrapInternal(page.nextCursor()),
                page.hasMore());
    }

    private static AuditAction parseAction(String action) {
        if (action == null || action.isBlank()) {
            return null;
        }
        try {
            return AuditAction.valueOf(action.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Unknown audit action: " + action);
        }
    }
}
