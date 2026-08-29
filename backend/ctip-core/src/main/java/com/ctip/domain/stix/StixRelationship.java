package com.ctip.domain.stix;

import com.ctip.domain.tenant.TenantId;
import com.ctip.sdk.Tlp;
import java.time.Instant;
import java.util.Objects;

/**
 * 一筆 stix_relationships 投影(docs/spec/04-data-dictionary.md 表 9)。
 *
 * <p>表 9 只有三元組與信封欄位、<strong>沒有 content 欄</strong>——JSON 不落庫,
 * 讀取時由 {@link StixRelationshipProjector#content} 以 Threat 聚合重建
 * (角色來自 {@code threat_indicators.role},存兩份會產生第二個真相來源)。
 */
public record StixRelationship(
        String stixId,
        String relationshipType,
        String sourceRef,
        String targetRef,
        TenantId ownerTenantId,
        Tlp tlp,
        Instant created,
        Instant modified) {

    public StixRelationship {
        Objects.requireNonNull(stixId, "stixId 不得為 null");
        Objects.requireNonNull(relationshipType, "relationshipType 不得為 null");
        Objects.requireNonNull(sourceRef, "sourceRef 不得為 null");
        Objects.requireNonNull(targetRef, "targetRef 不得為 null");
        Objects.requireNonNull(ownerTenantId, "ownerTenantId 不得為 null");
        Objects.requireNonNull(tlp, "tlp 不得為 null");
        Objects.requireNonNull(created, "created 不得為 null");
        Objects.requireNonNull(modified, "modified 不得為 null");
        if (sourceRef.equals(targetRef)) {
            throw new IllegalArgumentException("關聯的兩端不得相同(ck_sr_no_self)");
        }
    }
}
