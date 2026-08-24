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
 * tenants(docs/spec/04-data-dictionary.md 表 1)。updated_at 由應用層維護,不用 DB trigger。
 * 欄位為 package-private:entity 僅在本 package 內由 mapper/adapter 直接存取,無 accessor 樣板。
 */
@Entity
@Table(name = "tenants")
class TenantEntity {

    @Id
    UUID id;

    @Column(nullable = false, length = 64)
    String slug;

    @Column(nullable = false)
    String name;

    @Column(nullable = false, length = 32)
    String type;

    @Column(nullable = false, length = 32)
    String status;

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
