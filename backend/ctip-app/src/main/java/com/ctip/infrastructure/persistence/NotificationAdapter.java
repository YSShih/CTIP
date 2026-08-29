package com.ctip.infrastructure.persistence;

import com.ctip.application.notification.NotificationQuery;
import com.ctip.application.notification.NotificationRecord;
import com.ctip.application.port.NotificationPort;
import com.ctip.domain.notification.NotificationType;
import com.ctip.domain.shared.Cursor;
import com.ctip.domain.shared.CursorPage;
import com.ctip.domain.tenant.TenantId;
import com.ctip.sdk.Severity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * NotificationPort 的 JPA 實作(表 26,兩模型)。
 *
 * <p>可見範圍固定為 {@code tenant_id IN (呼叫者租戶, public tenant)} 且
 * {@code user_id IS NULL OR user_id = 呼叫者}——與 §7.9 的過濾同一條規則,由此處強制,
 * 呼叫端無從指定別的租戶。
 */
@Repository
@Transactional
class NotificationAdapter implements NotificationPort {

    private final NotificationJpaRepository jpa;
    private final NotificationStatements statements;

    NotificationAdapter(NotificationJpaRepository jpa, NotificationStatements statements) {
        this.jpa = jpa;
        this.statements = statements;
    }

    /**
     * {@code REQUIRES_NEW}(02 §2.4 對 AFTER_COMMIT 消費端的規則):呼叫端在已提交交易的
     * afterCommit 回呼內,預設的 {@code REQUIRED} 會去參與一個已經結束的交易。
     *
     * <p>實測補充(2026-08-29,ADR 0029):在本專案的 JPA + PostgreSQL 組合下,改成
     * {@code REQUIRED} 的寫入<strong>仍然會落庫</strong>——連線在 afterCommit 之後才歸還,
     * 歸還時還原 autoCommit 會把它一併提交。保留 {@code REQUIRES_NEW} 是為了讓這個寫入
     * 有自己明確的提交邊界,而不是依賴連線歸還的副作用;規格的規則也是這樣寫的。
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recordIfAbsent(NotificationRecord n) {
        return statements.insertIfAbsent(n);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<NotificationRecord> findByEventId(UUID eventId) {
        return jpa.findFirstByEventIdOrderByCreatedAtAsc(eventId).map(NotificationAdapter::toRecord);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage<NotificationRecord> list(NotificationQuery query) {
        List<NotificationEntity> rows = statements.page(query, visibleTenants(query.tenantId()));
        boolean hasMore = rows.size() > query.limit();
        List<NotificationRecord> items = new ArrayList<>(Math.min(rows.size(), query.limit()));
        for (int i = 0; i < Math.min(rows.size(), query.limit()); i++) {
            items.add(toRecord(rows.get(i)));
        }
        if (!hasMore || items.isEmpty()) {
            return CursorPage.lastPage(items);
        }
        NotificationRecord last = items.get(items.size() - 1);
        return new CursorPage<>(items, new Cursor(last.createdAt(), last.id()).encode(), true);
    }

    @Override
    public boolean markRead(UUID id, TenantId tenantId, UUID userId, Instant readAt) {
        return jpa.markRead(id, visibleTenants(tenantId), userId, readAt) > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public long countUnread(TenantId tenantId, UUID userId) {
        return jpa.countUnread(visibleTenants(tenantId), userId);
    }

    /** 本租戶 + public tenant 的平台通知(v2.0 修正的規格衝突 7:過濾是 IN,不是等於)。 */
    private static List<UUID> visibleTenants(TenantId tenantId) {
        return tenantId.isPublic()
                ? List.of(TenantId.PUBLIC.value())
                : List.of(tenantId.value(), TenantId.PUBLIC.value());
    }

    private static NotificationRecord toRecord(NotificationEntity e) {
        return new NotificationRecord(
                e.id,
                new TenantId(e.tenantId),
                e.userId,
                e.eventId,
                NotificationType.valueOf(e.eventType),
                e.title,
                e.body,
                Severity.valueOf(e.severity),
                e.resourceType,
                e.resourceId,
                e.readAt,
                e.createdAt);
    }
}
