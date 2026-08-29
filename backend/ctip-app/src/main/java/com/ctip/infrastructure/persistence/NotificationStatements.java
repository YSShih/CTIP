package com.ctip.infrastructure.persistence;

import com.ctip.application.notification.NotificationQuery;
import com.ctip.application.notification.NotificationRecord;
import com.ctip.domain.shared.Cursor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * {@code notifications} 的兩個 native 述句。
 *
 * <p>寫在 {@link EntityManager} 上而不是 Spring Data 的 {@code @Query}:兩者都需要十個以上的
 * 參數,而參數上限是 5(§1.8 規則 3)。在這裡它們是<strong>一個 record 一個參數</strong>,
 * 述句本身也留在同一個地方看得到。
 */
@Component
class NotificationStatements {

    /**
     * 冪等寫入(§13.1 規則 5)。必須是 native:唯一索引 {@code ux_notif_idempotent} 建在
     * {@code COALESCE(user_id, …)} 這個<strong>運算式</strong>上(PostgreSQL 的 UNIQUE 對 null 不去重),
     * JPA 的 persist 沒有辦法表達 {@code ON CONFLICT DO NOTHING}。
     */
    private static final String INSERT_IF_ABSENT = """
            INSERT INTO notifications (id, tenant_id, user_id, event_id, event_type,
                                       title, body, severity, resource_type, resource_id, created_at)
            VALUES (:id, :tenantId, :userId, :eventId, :eventType,
                    :title, :body, :severity, :resourceType, :resourceId, :createdAt)
            ON CONFLICT DO NOTHING
            """;

    /**
     * 通知中心的一頁。可見範圍是「本租戶 + public tenant 的平台通知」,再加上
     * 「廣播列或指定給我的列」——與 §7.9 的 {@code IN (current, public)} 同一條規則。
     *
     * <p>keyset 走 {@code ix_notif_tenant_created};{@code CAST(… AS timestamptz)} 不可省:
     * 參數為 null 時 PostgreSQL 推不出型別({@code could not determine data type})。
     */
    private static final String PAGE = """
            SELECT * FROM notifications
            WHERE tenant_id IN (:tenantIds)
              AND (user_id IS NULL OR user_id = :userId)
              AND (:unreadOnly = FALSE OR read_at IS NULL)
              AND (CAST(:cursorCreatedAt AS timestamptz) IS NULL
                   OR created_at < CAST(:cursorCreatedAt AS timestamptz)
                   OR (created_at = CAST(:cursorCreatedAt AS timestamptz)
                       AND id < CAST(:cursorId AS uuid)))
            ORDER BY created_at DESC, id DESC
            LIMIT :maxRows
            """;

    @PersistenceContext
    private EntityManager entityManager;

    /** @return true 表示這次真的插入了;false 表示同一個 {@code eventId} 已有通知列 */
    boolean insertIfAbsent(NotificationRecord n) {
        return entityManager
                        .createNativeQuery(INSERT_IF_ABSENT)
                        .setParameter("id", n.id())
                        .setParameter("tenantId", n.tenantId().value())
                        .setParameter("userId", n.userId())
                        .setParameter("eventId", n.eventId())
                        .setParameter("eventType", n.eventType().name())
                        .setParameter("title", n.title())
                        .setParameter("body", n.body())
                        .setParameter("severity", n.severity().name())
                        .setParameter("resourceType", n.resourceType())
                        .setParameter("resourceId", n.resourceId())
                        .setParameter("createdAt", n.createdAt())
                        .executeUpdate()
                > 0;
    }

    @SuppressWarnings("unchecked")
    List<NotificationEntity> page(NotificationQuery query, List<UUID> visibleTenants) {
        Cursor cursor = query.cursor();
        return entityManager
                .createNativeQuery(PAGE, NotificationEntity.class)
                .setParameter("tenantIds", visibleTenants)
                .setParameter("userId", query.userId())
                .setParameter("unreadOnly", query.unreadOnly())
                .setParameter("cursorCreatedAt", cursor == null ? null : cursor.lastSeen())
                .setParameter("cursorId", cursor == null ? null : cursor.id())
                .setParameter("maxRows", query.limit() + 1)
                .getResultList();
    }
}
