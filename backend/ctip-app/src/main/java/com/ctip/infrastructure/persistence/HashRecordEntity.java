package com.ctip.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** hash_records(docs/spec/04-data-dictionary.md 表 6):平台去重指紋,與 IOC 檔案雜湊是兩件不同的事。 */
@Entity
@Table(name = "hash_records")
class HashRecordEntity {

    @Id
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "indicator_id", nullable = false)
    IndicatorEntity indicator;

    @Column(name = "source_id")
    UUID sourceId;

    @Column(nullable = false, length = 16)
    String algorithm;

    @Column(nullable = false, length = 128)
    String digest;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }
}
