package com.ctip.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * threat_indicators(docs/spec/04-data-dictionary.md 表 20):PK 為 (threat_id, indicator_id)。
 * 只存 {@code indicator_id}——不映射到 IndicatorEntity(不變量 H5:不得持有 Indicator 物件,
 * 若在此掛 {@code @ManyToOne IndicatorEntity},重建聚合時就會把整個 Indicator 拉進 Threat)。
 * 類別 public 供 ThreatSpecifications 的關聯子查詢引用;欄位仍 package-private。
 */
@Entity
@Table(name = "threat_indicators")
@IdClass(ThreatIndicatorKey.class)
public class ThreatIndicatorEntity {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "threat_id", nullable = false)
    ThreatEntity threat;

    @Id
    @Column(name = "indicator_id", nullable = false)
    UUID indicatorId;

    @Column(nullable = false, length = 32)
    String role;

    @Column(name = "added_at", nullable = false)
    Instant addedAt;

    @PrePersist
    void onCreate() {
        addedAt = addedAt == null ? Instant.now() : addedAt;
    }
}
