package com.ctip.application.threat;

import com.ctip.domain.threat.ThreatType;
import com.ctip.sdk.Severity;
import com.ctip.sdk.Tlp;
import java.time.Instant;
import java.util.Set;

/**
 * 建立 Threat 的請求(docs/spec/09-api.md §9.1「Threat — 寫入」)。
 *
 * <p>id、firstSeen/lastSeen 的預設值由 {@link ThreatService} 以 port 補齊——
 * 呼叫端(controller)不得自行產生 UUID 或讀時鐘。
 */
public record CreateThreatCommand(
        ThreatType type,
        String name,
        Set<String> aliases,
        String description,
        Severity severity,
        Integer confidence,
        Tlp tlp,
        Set<String> tags,
        Instant firstSeen,
        Instant lastSeen) {

    public CreateThreatCommand {
        aliases = aliases == null ? Set.of() : Set.copyOf(aliases);
        tags = tags == null ? Set.of() : Set.copyOf(tags);
    }
}
