package com.ctip.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** notifications(docs/spec/04-data-dictionary.md 表 26;兩模型)。user_id 為 null = 全租戶廣播。 */
@Entity
@Table(name = "notifications")
class NotificationEntity {

    @Id
    UUID id;

    @Column(name = "tenant_id", nullable = false)
    UUID tenantId;

    @Column(name = "user_id")
    UUID userId;

    @Column(name = "event_id", nullable = false)
    UUID eventId;

    @Column(name = "event_type", nullable = false, length = 64)
    String eventType;

    @Column(nullable = false, length = 255)
    String title;

    @Column
    String body;

    @Column(nullable = false, length = 16)
    String severity;

    @Column(name = "resource_type", length = 64)
    String resourceType;

    @Column(name = "resource_id")
    UUID resourceId;

    @Column(name = "read_at")
    Instant readAt;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;
}
