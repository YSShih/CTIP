package com.ctip.domain.threat;

import com.ctip.domain.tenant.TenantId;
import com.ctip.sdk.Confidence;
import com.ctip.sdk.Severity;
import com.ctip.sdk.Tlp;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/** Threat 聚合的持久化快照(重建與寫出用;record 建構子自動豁免參數數限制)。 */
public record ThreatSnapshot(
        ThreatId id,
        TenantId ownerTenantId,
        ThreatType type,
        String name,
        Set<String> aliases,
        String description,
        Severity severity,
        Confidence confidence,
        Tlp tlp,
        ThreatStatus status,
        Instant firstSeen,
        Instant lastSeen,
        Set<String> tags,
        List<ThreatIndicatorLink> indicators,
        List<ExternalReference> externalReferences) {

    public ThreatSnapshot {
        aliases = aliases == null ? Set.of() : Set.copyOf(aliases);
        tags = tags == null ? Set.of() : Set.copyOf(tags);
        indicators = indicators == null ? List.of() : List.copyOf(indicators);
        externalReferences = externalReferences == null ? List.of() : List.copyOf(externalReferences);
    }
}
