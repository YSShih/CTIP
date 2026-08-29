package com.ctip.infrastructure.persistence;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * threat_indicators 的複合主鍵 (threat_id, indicator_id)(表 20)。
 * {@code threat} 是 derived identity:欄位型別為 ThreatEntity 的 PK 型別。
 */
class ThreatIndicatorKey implements Serializable {

    @java.io.Serial
    private static final long serialVersionUID = 1L;

    UUID threat;
    UUID indicatorId;

    ThreatIndicatorKey() {}

    ThreatIndicatorKey(UUID threat, UUID indicatorId) {
        this.threat = threat;
        this.indicatorId = indicatorId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ThreatIndicatorKey key)) {
            return false;
        }
        return Objects.equals(threat, key.threat) && Objects.equals(indicatorId, key.indicatorId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(threat, indicatorId);
    }
}
