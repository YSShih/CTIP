package com.ctip.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** subscriptions(docs/spec/04-data-dictionary.md 表 18)。B1 由部分唯一索引 ux_subscriptions_active 強制。 */
@Entity
@Table(name = "subscriptions")
class SubscriptionEntity {

    @Id
    UUID id;

    @Column(name = "tenant_id", nullable = false)
    UUID tenantId;

    @Column(name = "plan_id", nullable = false)
    UUID planId;

    @Column(nullable = false, length = 32)
    String status;

    @Column(nullable = false, length = 32)
    String provider;

    @Column(name = "external_subscription_id", length = 255)
    String externalSubscriptionId;

    @Column(name = "current_period_start", nullable = false)
    Instant currentPeriodStart;

    @Column(name = "current_period_end")
    Instant currentPeriodEnd;

    @Column(name = "cancelled_at")
    Instant cancelledAt;

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
