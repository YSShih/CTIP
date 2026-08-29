package com.ctip.domain.stix;

import com.ctip.domain.indicator.IndicatorId;
import com.ctip.domain.threat.ThreatId;

/**
 * 一筆 stix_objects 的來源 domain 物件(表 8 的 {@code indicator_id} / {@code threat_id};
 * 受 {@code ck_so_origin} 約束,至多一個非 null)。
 *
 * <p>{@code observed-data} 的 STIX id 是決定性雜湊、{@code identity} 的來源是 Source——
 * 兩者都無法從 id 反推出要檢查誰的可見度,只能問這一列自己記的來源。
 */
public record StixOrigin(IndicatorId indicatorId, ThreatId threatId) {

    public StixOrigin {
        if (indicatorId != null && threatId != null) {
            throw new IllegalArgumentException("一筆 STIX 投影只能有一個來源 domain 物件(ck_so_origin)");
        }
    }

    /** 兩者皆 null:與任何聚合無關的投影(M2 只有 identity ← Source)。 */
    public boolean isStandalone() {
        return indicatorId == null && threatId == null;
    }
}
