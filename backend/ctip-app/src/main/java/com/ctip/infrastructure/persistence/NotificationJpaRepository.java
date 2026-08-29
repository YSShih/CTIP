package com.ctip.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * {@code notifications} 的簡單查詢;冪等寫入與 keyset 分頁在
 * {@link NotificationStatements}(那兩句的參數個數超過 §1.8 規則 3 的上限)。
 */
interface NotificationJpaRepository extends JpaRepository<NotificationEntity, UUID> {

    Optional<NotificationEntity> findFirstByEventIdOrderByCreatedAtAsc(UUID eventId);

    /** 只標記可見範圍內的那一列;跨租戶或別人的私人通知一律標不到(回 0 → 404)。 */
    @Modifying
    @Query(value = """
                    UPDATE notifications SET read_at = :readAt
                    WHERE id = :id
                      AND tenant_id IN (:tenantIds)
                      AND (user_id IS NULL OR user_id = :userId)
                      AND read_at IS NULL
                    """, nativeQuery = true)
    int markRead(
            @Param("id") UUID id,
            @Param("tenantIds") List<UUID> tenantIds,
            @Param("userId") UUID userId,
            @Param("readAt") Instant readAt);

    @Query(value = """
                    SELECT count(*) FROM notifications
                    WHERE tenant_id IN (:tenantIds)
                      AND (user_id IS NULL OR user_id = :userId)
                      AND read_at IS NULL
                    """, nativeQuery = true)
    long countUnread(@Param("tenantIds") List<UUID> tenantIds, @Param("userId") UUID userId);
}
