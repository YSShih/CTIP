package com.ctip.application.audit;

import com.ctip.application.port.AuditLogPort;
import com.ctip.domain.shared.CursorPage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 稽核軌跡查詢(docs/spec/09-api.md §9.1 的 {@code GET /audit-logs},權限 {@code audit:read})。 */
@Service
public class AuditQueryService {

    private final AuditLogPort auditLogs;

    public AuditQueryService(AuditLogPort auditLogs) {
        this.auditLogs = auditLogs;
    }

    @Transactional(readOnly = true)
    public CursorPage<AuditRecord> list(AuditLogQuery query) {
        return auditLogs.list(query);
    }
}
