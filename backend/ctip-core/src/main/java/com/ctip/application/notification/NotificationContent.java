package com.ctip.application.notification;

import com.ctip.domain.event.DomainEvent;
import com.ctip.domain.notification.EventContext;
import com.ctip.domain.notification.NotificationEvent;
import com.ctip.domain.notification.NotificationType;
import com.ctip.sdk.IocType;
import com.ctip.sdk.Severity;
import java.util.Set;
import java.util.UUID;

/**
 * 一則通知的「內容」面(標題、嚴重度、指向的資源),與「過濾」面(iocTypes / tags / sourceIds)分開。
 *
 * <p>{@link NotificationEventFactory} 決定內容,過濾欄位則要從聚合補齊;
 * 兩者放在一起會讓那個類別的每個分支都在重複組裝十四個參數。
 *
 * @param userId 非 null = 只有該使用者看得到(例:token 重用);null = 全租戶廣播
 */
record NotificationContent(
        NotificationType type,
        Severity severity,
        String title,
        String body,
        String resourceType,
        UUID resourceId,
        UUID userId) {

    /** 不需要過濾維度的通知(方案異動、系統警示……)。 */
    NotificationEvent toEvent(EventContext context, DomainEvent event) {
        return toEvent(context, event, Set.of(), Set.of(), Set.of());
    }

    NotificationEvent toEvent(
            EventContext context, DomainEvent event, Set<IocType> iocTypes, Set<String> tags, Set<UUID> sourceIds) {
        return new NotificationEvent(
                context.eventId(),
                type,
                event.tenantId(),
                context.occurredAt(),
                context.traceId(),
                title,
                body,
                severity,
                resourceType,
                resourceId,
                userId,
                iocTypes,
                tags,
                sourceIds);
    }

    NotificationContent withSeverity(Severity replacement) {
        return new NotificationContent(type, replacement, title, body, resourceType, resourceId, userId);
    }

    /** 來源健康與攝取失敗共用 {@code SOURCE_FAILURE}——七種型別裡只有這一個承載來源狀態。 */
    static NotificationEvent sourceHealth(
            EventContext context, DomainEvent event, UUID sourceId, Severity severity, String title) {
        return new NotificationContent(NotificationType.SOURCE_FAILURE, severity, title, null, "source", sourceId, null)
                .toEvent(context, event, Set.of(), Set.of(), Set.of(sourceId));
    }
}
