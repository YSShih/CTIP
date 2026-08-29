package com.ctip.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * webhooks(docs/spec/04-data-dictionary.md 表 24)。
 * secret 以 AES-GCM 密文儲存(不變量 W2 定調;ADR 0021),明文不落庫也不進日誌。
 */
@Entity
@Table(name = "webhooks")
class WebhookEntity {

    @Id
    UUID id;

    @Column(name = "tenant_id", nullable = false)
    UUID tenantId;

    @Column(name = "created_by_user_id", nullable = false)
    UUID createdByUserId;

    @Column(nullable = false, length = 128)
    String name;

    @Column(name = "target_url", nullable = false, length = 2048)
    String targetUrl;

    @Column(name = "secret_encrypted", nullable = false)
    byte[] secretEncrypted;

    @Column(name = "event_types", nullable = false, columnDefinition = "text[]")
    String[] eventTypes;

    @Column(name = "filter_ioc_types", nullable = false, columnDefinition = "text[]")
    String[] filterIocTypes;

    @Column(name = "filter_min_severity", length = 16)
    String filterMinSeverity;

    @Column(name = "filter_tags", nullable = false, columnDefinition = "text[]")
    String[] filterTags;

    @Column(name = "filter_source_ids", nullable = false, columnDefinition = "uuid[]")
    UUID[] filterSourceIds;

    @Column(nullable = false, length = 16)
    String status;

    @Column(name = "consecutive_failures", nullable = false)
    short consecutiveFailures;

    @Column(name = "last_delivery_at")
    Instant lastDeliveryAt;

    @Column(name = "last_success_at")
    Instant lastSuccessAt;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = createdAt == null ? now : createdAt;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
