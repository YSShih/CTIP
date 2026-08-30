package com.ctip.infrastructure.persistence;

import com.ctip.application.audit.AuditActorSummary;
import com.ctip.application.audit.AuditLogQuery;
import com.ctip.application.audit.AuditRecord;
import com.ctip.domain.shared.Cursor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * {@code audit_logs} 的兩個 native 述句(表 27)。
 *
 * <p>寫在 {@link EntityManager} 上而不是 Spring Data:欄位有 13 個、參數上限是 5
 * (§1.8 規則 3),且 {@code inet} 與 {@code jsonb} 都需要顯式 CAST——參數為字串時
 * PostgreSQL 推不出型別。與 {@code NotificationStatements} 同一個作法。
 */
@Component
class AuditLogStatements {

    private static final String INSERT = """
            INSERT INTO audit_logs (id, occurred_at, actor_type, actor_id, tenant_id, action,
                                    resource_type, resource_id, ip, user_agent, result, trace_id, metadata)
            VALUES (:id, :occurredAt, :actorType, :actorId, :tenantId, :action,
                    :resourceType, :resourceId, CAST(:ip AS inet), :userAgent, :result, :traceId,
                    CAST(:metadata AS jsonb))
            """;

    /**
     * 一頁稽核軌跡。keyset 走 {@code ix_al_tenant_time};{@code CAST(… AS timestamptz)} 不可省:
     * 參數為 null 時 PostgreSQL 推不出型別({@code could not determine data type})。
     */
    private static final String PAGE = """
            SELECT * FROM audit_logs
            WHERE tenant_id = :tenantId
              AND (CAST(:action AS varchar) IS NULL OR action = CAST(:action AS varchar))
              AND (CAST(:cursorAt AS timestamptz) IS NULL
                   OR occurred_at < CAST(:cursorAt AS timestamptz)
                   OR (occurred_at = CAST(:cursorAt AS timestamptz)
                       AND id < CAST(:cursorId AS uuid)))
            ORDER BY occurred_at DESC, id DESC
            LIMIT :maxRows
            """;

    /** 資料主體查詢(§13.4):某個行為者留下的足跡有多少、從何時到何時。 */
    private static final String ACTOR_SUMMARY =
            "SELECT count(*), min(occurred_at), max(occurred_at) FROM audit_logs WHERE actor_id = :actorId";

    @PersistenceContext
    private EntityManager entityManager;

    AuditActorSummary summarizeActor(UUID actorId) {
        Object[] row = (Object[]) entityManager
                .createNativeQuery(ACTOR_SUMMARY)
                .setParameter("actorId", actorId)
                .getSingleResult();
        long count = ((Number) row[0]).longValue();
        return count == 0
                ? AuditActorSummary.empty()
                : new AuditActorSummary(count, toInstant(row[1]), toInstant(row[2]));
    }

    private static Instant toInstant(Object value) {
        return value instanceof Instant instant ? instant : ((java.sql.Timestamp) value).toInstant();
    }

    void insert(AuditRecord row) {
        Query query = entityManager
                .createNativeQuery(INSERT)
                .setParameter("id", row.id())
                .setParameter("occurredAt", row.occurredAt())
                .setParameter("actorType", row.actorType().name())
                .setParameter("actorId", row.actorId())
                .setParameter("tenantId", row.tenantId().value())
                .setParameter("action", row.action().name());
        bindDetails(query, row).executeUpdate();
    }

    private static Query bindDetails(Query query, AuditRecord row) {
        return query.setParameter("resourceType", row.resourceType())
                .setParameter("resourceId", row.resourceId())
                .setParameter("ip", row.ip())
                .setParameter("userAgent", row.userAgent())
                .setParameter("result", row.result().name())
                .setParameter("traceId", row.traceId())
                .setParameter("metadata", JsonPayloads.toJson(row.metadata()));
    }

    @SuppressWarnings("unchecked")
    List<AuditLogEntity> page(AuditLogQuery query) {
        Cursor cursor = query.cursor();
        return entityManager
                .createNativeQuery(PAGE, AuditLogEntity.class)
                .setParameter("tenantId", query.tenantId().value())
                .setParameter(
                        "action", query.action() == null ? null : query.action().name())
                .setParameter("cursorAt", cursor == null ? null : cursor.lastSeen())
                .setParameter("cursorId", cursor == null ? null : cursor.id())
                .setParameter("maxRows", query.limit() + 1)
                .getResultList();
    }
}
