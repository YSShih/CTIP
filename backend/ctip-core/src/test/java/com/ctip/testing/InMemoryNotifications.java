package com.ctip.testing;

import com.ctip.application.notification.NotificationQuery;
import com.ctip.application.notification.NotificationRecord;
import com.ctip.application.port.NotificationPort;
import com.ctip.domain.shared.CursorPage;
import com.ctip.domain.tenant.TenantId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 測試用 in-memory NotificationPort。
 * {@code ux_notif_idempotent} 的去重鍵以 {@code (eventId, tenantId, userId)} 模擬——
 * 真正的 {@code COALESCE} 唯一索引由 {@code EventIdempotencyTest} 對真資料庫驗證。
 */
public final class InMemoryNotifications implements NotificationPort {

    private final List<NotificationRecord> store = new ArrayList<>();

    @Override
    public boolean recordIfAbsent(NotificationRecord notification) {
        boolean exists = store.stream().anyMatch(existing -> sameKey(existing, notification));
        if (exists) {
            return false;
        }
        store.add(notification);
        return true;
    }

    @Override
    public Optional<NotificationRecord> findByEventId(UUID eventId) {
        return store.stream()
                .filter(notification -> notification.eventId().equals(eventId))
                .findFirst();
    }

    @Override
    public CursorPage<NotificationRecord> list(NotificationQuery query) {
        return CursorPage.lastPage(store.stream()
                .filter(notification -> visibleTo(notification, query))
                .filter(notification -> !query.unreadOnly() || notification.readAt() == null)
                .limit(query.limit())
                .toList());
    }

    @Override
    public boolean markRead(UUID id, TenantId tenantId, UUID userId, Instant readAt) {
        for (int i = 0; i < store.size(); i++) {
            NotificationRecord current = store.get(i);
            if (current.id().equals(id) && current.readAt() == null) {
                store.set(i, read(current, readAt));
                return true;
            }
        }
        return false;
    }

    @Override
    public long countUnread(TenantId tenantId, UUID userId) {
        return store.stream()
                .filter(notification -> notification.readAt() == null)
                .count();
    }

    public List<NotificationRecord> all() {
        return List.copyOf(store);
    }

    private static boolean sameKey(NotificationRecord a, NotificationRecord b) {
        return a.eventId().equals(b.eventId())
                && a.tenantId().equals(b.tenantId())
                && Objects.equals(a.userId(), b.userId());
    }

    private static boolean visibleTo(NotificationRecord notification, NotificationQuery query) {
        boolean tenantMatches =
                notification.tenantId().isPublic() || notification.tenantId().equals(query.tenantId());
        boolean userMatches =
                notification.userId() == null || notification.userId().equals(query.userId());
        return tenantMatches && userMatches;
    }

    private static NotificationRecord read(NotificationRecord notification, Instant readAt) {
        return new NotificationRecord(
                notification.id(),
                notification.tenantId(),
                notification.userId(),
                notification.eventId(),
                notification.eventType(),
                notification.title(),
                notification.body(),
                notification.severity(),
                notification.resourceType(),
                notification.resourceId(),
                readAt,
                notification.createdAt());
    }
}
