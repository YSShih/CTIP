package com.ctip.domain.notification;

/**
 * 通知事件型別(docs/spec/13-platform-ops.md §13.2、04 §4.5)。
 * 同時是 {@code notifications.event_type}、{@code webhooks.event_types} 與 Kafka payload 的取值。
 *
 * <p>這一份清單是<strong>封閉的</strong>:§13.2 明列七項,不得因為某個 domain event
 * 對不上而新增型別。domain event → 型別的對應見 {@code docs/api/events/README.md}。
 */
public enum NotificationType {
    NEW_IOC,
    THREAT_UPDATED,
    IOC_REVOKED,
    SOURCE_FAILURE,
    SUBSCRIPTION_CHANGED,
    SYNC_SNAPSHOT_READY,
    SYSTEM_ALERT
}
