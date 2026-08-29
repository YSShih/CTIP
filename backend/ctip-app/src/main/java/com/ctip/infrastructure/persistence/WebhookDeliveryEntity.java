package com.ctip.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** webhook_deliveries(docs/spec/04-data-dictionary.md 表 25;append-only,兩模型)。 */
@Entity
@Table(name = "webhook_deliveries")
class WebhookDeliveryEntity {

    @Id
    UUID id;

    @Column(name = "webhook_id", nullable = false)
    UUID webhookId;

    @Column(name = "event_id", nullable = false)
    UUID eventId;

    @Column(name = "event_type", nullable = false, length = 64)
    String eventType;

    @Column(nullable = false)
    short attempt;

    @Column(nullable = false, length = 16)
    String status;

    @Column(name = "http_status")
    Short httpStatus;

    @Column(name = "response_time_ms")
    Integer responseTimeMs;

    @Column(name = "error_message")
    String errorMessage;

    @Column(name = "next_retry_at")
    Instant nextRetryAt;

    @Column(name = "delivered_at")
    Instant deliveredAt;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;
}
