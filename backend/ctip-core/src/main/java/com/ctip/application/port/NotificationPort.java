package com.ctip.application.port;

import com.ctip.application.notification.NotificationQuery;
import com.ctip.application.notification.NotificationRecord;
import com.ctip.domain.shared.CursorPage;
import com.ctip.domain.tenant.TenantId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** {@code notifications} 的讀寫 port(兩模型表,無 domain model)。 */
public interface NotificationPort {

    /**
     * 冪等寫入(docs/spec/13-platform-ops.md §13.1 規則 5)。
     *
     * @return true 表示這次真的插入了;false 表示 {@code ux_notif_idempotent} 已有同一個
     *     {@code (eventId, tenantId, userId)},即事件重送
     */
    boolean recordIfAbsent(NotificationRecord notification);

    /**
     * 以 {@code eventId} 取回已落庫的通知——重送與重試共用同一份內容。
     *
     * <p>送達 payload 是這一列的<strong>純函數</strong>({@code WebhookPayloadPort}),
     * 因此第 1 次與第 5 次嘗試送出的 body 一定相同;表 25 沒有 payload 欄位,
     * 重試若各自重新組裝就會漂移。
     */
    Optional<NotificationRecord> findByEventId(UUID eventId);

    CursorPage<NotificationRecord> list(NotificationQuery query);

    /**
     * 標記為已讀。
     *
     * @return false 表示該通知不存在或不屬於呼叫者的可見範圍(controller 據此回 404)
     */
    boolean markRead(UUID id, TenantId tenantId, UUID userId, Instant readAt);

    long countUnread(TenantId tenantId, UUID userId);
}
