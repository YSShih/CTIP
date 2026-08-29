package com.ctip.testing;

import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.threat.NewThreatCommand;
import com.ctip.domain.threat.Threat;
import com.ctip.domain.threat.ThreatId;
import com.ctip.domain.threat.ThreatType;
import com.ctip.sdk.Confidence;
import com.ctip.sdk.Severity;
import com.ctip.sdk.Tlp;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** 測試 fixture(docs/spec/14-testing.md §14.7),僅供測試使用。時間一律固定值。 */
public final class ThreatTestBuilder {

    public static final Instant T0 = Instant.parse("2026-08-01T00:00:00Z");
    public static final ThreatId THREAT_ID = new ThreatId(UUID.fromString("00000000-0000-0000-0000-00000000beef"));

    private ThreatTestBuilder() {}

    public static Threat malwareFamily(TenantId owner, Tlp tlp) {
        return threat(THREAT_ID, owner, ThreatType.MALWARE_FAMILY, "AgentTesla", tlp);
    }

    public static Threat threat(ThreatId id, TenantId owner, ThreatType type, String name, Tlp tlp) {
        return Threat.create(new NewThreatCommand(
                id,
                owner,
                type,
                name,
                Set.of("Agent Tesla"),
                "Commodity infostealer.",
                Severity.HIGH,
                Confidence.of(70),
                tlp,
                Set.of("infostealer"),
                T0,
                T0));
    }
}
