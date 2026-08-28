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
 * plans(docs/spec/04-data-dictionary.md 表 17)。
 * 可為 null 的配額欄位一律用包裝型別:{@code null} = 無限制、{@code 0} = 停用(ADR 0019),
 * 用原始型別會把 null 讀成 0,把「無限制」變成「停用」。
 */
@Entity
@Table(name = "plans")
class PlanEntity {

    @Id
    UUID id;

    @Column(nullable = false, length = 32)
    String code;

    @Column(nullable = false, length = 64)
    String name;

    @Column(nullable = false)
    short tier;

    @Column(name = "requests_per_minute", nullable = false)
    int requestsPerMinute;

    @Column(name = "requests_per_day")
    Integer requestsPerDay;

    @Column(name = "max_page_size", nullable = false)
    int maxPageSize;

    @Column(name = "max_batch_lookup", nullable = false)
    int maxBatchLookup;

    @Column(name = "min_sync_interval_seconds", nullable = false)
    int minSyncIntervalSeconds;

    @Column(name = "public_bloom_enabled", nullable = false)
    boolean publicBloomEnabled;

    @Column(name = "tenant_bloom_capacity")
    Long tenantBloomCapacity;

    @Column(name = "websocket_enabled", nullable = false)
    boolean websocketEnabled;

    @Column(name = "max_webhooks", nullable = false)
    int maxWebhooks;

    @Column(name = "max_api_keys", nullable = false)
    int maxApiKeys;

    @Column(name = "custom_feed_enabled", nullable = false)
    boolean customFeedEnabled;

    @Column(name = "stix_export_max_objects")
    Integer stixExportMaxObjects;

    @Column(name = "max_manual_submissions_per_day", nullable = false)
    int maxManualSubmissionsPerDay;

    @Column(name = "max_import_rows_per_file", nullable = false)
    int maxImportRowsPerFile;

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
