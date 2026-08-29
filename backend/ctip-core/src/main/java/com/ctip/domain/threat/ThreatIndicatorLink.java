package com.ctip.domain.threat;

import com.ctip.domain.indicator.IndicatorId;
import java.time.Instant;
import java.util.Objects;

/**
 * Threat 與 Indicator 的關聯(docs/spec/02-ddd-model.md §2.3 H5、04 表 20)。
 *
 * <p><strong>只存 {@link IndicatorId},不持有 {@code Indicator} 物件</strong>——跨聚合只能以 ID 參照
 * (§2.2)。這也是 H6 必須降格為應用層一致性規則的原因:聚合內部拿不到關聯 Indicator 的 TLP。
 *
 * <p>03 §3.2.7 把它標為聚合內部實體(識別為 {@code indicatorId},{@code addedAt} 不隨角色變更而改);
 * 此處實作為不可變 record,改角色由聚合以新值取代同一個 {@code indicatorId} 的項目——
 * 聚合快照因此不會外洩可變物件。
 */
public record ThreatIndicatorLink(IndicatorId indicatorId, IndicatorRole role, Instant addedAt) {

    public ThreatIndicatorLink {
        Objects.requireNonNull(indicatorId, "indicatorId 不得為 null");
        Objects.requireNonNull(role, "role 不得為 null");
        Objects.requireNonNull(addedAt, "addedAt 不得為 null");
    }

    ThreatIndicatorLink withRole(IndicatorRole newRole) {
        return new ThreatIndicatorLink(indicatorId, newRole, addedAt);
    }
}
