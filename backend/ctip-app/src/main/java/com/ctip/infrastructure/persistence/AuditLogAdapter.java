package com.ctip.infrastructure.persistence;

import com.ctip.application.audit.AuditActorSummary;
import com.ctip.application.audit.AuditLogQuery;
import com.ctip.application.audit.AuditRecord;
import com.ctip.application.port.AuditLogPort;
import com.ctip.domain.audit.AuditAction;
import com.ctip.domain.audit.AuditActorType;
import com.ctip.domain.audit.AuditResult;
import com.ctip.domain.shared.Cursor;
import com.ctip.domain.shared.CursorPage;
import com.ctip.domain.tenant.TenantId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link AuditLogPort} 的 JPA 實作(表 27,兩模型、append-only)。
 *
 * <p>{@code append} 用 {@code REQUIRES_NEW}:稽核寫入由自己的執行緒發動,絕不可以參與
 * 業務交易——業務交易回滾時,「這件事發生過」的紀錄仍然必須留下(§13.5 規則 3 的另一面)。
 */
@Repository
class AuditLogAdapter implements AuditLogPort {

    private final AuditLogStatements statements;

    AuditLogAdapter(AuditLogStatements statements) {
        this.statements = statements;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void append(List<AuditRecord> records) {
        records.forEach(statements::insert);
    }

    @Override
    @Transactional(readOnly = true)
    public AuditActorSummary summarizeActor(UUID actorId) {
        return statements.summarizeActor(actorId);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage<AuditRecord> list(AuditLogQuery query) {
        List<AuditLogEntity> rows = statements.page(query);
        int size = Math.min(rows.size(), query.limit());
        List<AuditRecord> items = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            items.add(toRecord(rows.get(i)));
        }
        if (rows.size() <= query.limit() || items.isEmpty()) {
            return CursorPage.lastPage(items);
        }
        AuditRecord last = items.get(items.size() - 1);
        return new CursorPage<>(items, new Cursor(last.occurredAt(), last.id()).encode(), true);
    }

    private static AuditRecord toRecord(AuditLogEntity e) {
        return new AuditRecord(
                e.id,
                e.occurredAt,
                AuditActorType.valueOf(e.actorType),
                e.actorId,
                new TenantId(e.tenantId),
                AuditAction.valueOf(e.action),
                e.resourceType,
                e.resourceId,
                e.ip,
                e.userAgent,
                AuditResult.valueOf(e.result),
                e.traceId,
                JsonPayloads.toMap(e.metadata));
    }
}
