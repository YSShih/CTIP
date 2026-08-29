package com.ctip.domain.threat;

import com.ctip.domain.tenant.TenantId;
import com.ctip.sdk.Confidence;
import com.ctip.sdk.Severity;
import com.ctip.sdk.Tlp;
import java.time.Instant;
import java.util.Set;

/**
 * 建立 Threat 的輸入(id 與時間由 application 以 IdGeneratorPort / ClockPort 提供——
 * domain 不得呼叫 {@code UUID.randomUUID()} 或 {@code Instant.now()},規則 23)。
 */
public record NewThreatCommand(
        ThreatId id,
        TenantId ownerTenantId,
        ThreatType type,
        String name,
        Set<String> aliases,
        String description,
        Severity severity,
        Confidence confidence,
        Tlp tlp,
        Set<String> tags,
        Instant firstSeen,
        Instant lastSeen) {

    public NewThreatCommand {
        aliases = aliases == null ? Set.of() : Set.copyOf(aliases);
        tags = tags == null ? Set.of() : Set.copyOf(tags);
    }
}
