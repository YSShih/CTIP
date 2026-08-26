package com.ctip.domain.stix;

import com.ctip.domain.indicator.IndicatorId;
import com.ctip.domain.tenant.TenantId;
import com.ctip.sdk.Tlp;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * 一筆 stix_objects 投影(docs/spec/04-data-dictionary.md 表 8)。
 * 衍生投影,domain model 才是 source of truth;content 由 app 層序列化為 JSONB。
 */
public record StixProjection(
        String stixId,
        String stixType,
        TenantId ownerTenantId,
        IndicatorId indicatorId,
        Tlp tlp,
        Instant created,
        Instant modified,
        Map<String, Object> content) {

    public StixProjection {
        Objects.requireNonNull(stixId, "stixId 不得為 null");
        Objects.requireNonNull(stixType, "stixType 不得為 null");
        Objects.requireNonNull(ownerTenantId, "ownerTenantId 不得為 null");
        Objects.requireNonNull(tlp, "tlp 不得為 null");
        Objects.requireNonNull(created, "created 不得為 null");
        Objects.requireNonNull(modified, "modified 不得為 null");
        Objects.requireNonNull(content, "content 不得為 null");
    }
}
