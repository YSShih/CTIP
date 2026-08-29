package com.ctip.interfaces.rest;

import com.ctip.domain.source.Reputation;
import com.ctip.domain.source.SourceHealth;
import com.ctip.domain.source.SourceId;
import com.ctip.domain.source.SourceSnapshot;
import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.threat.NewThreatCommand;
import com.ctip.domain.threat.Threat;
import com.ctip.domain.threat.ThreatId;
import com.ctip.domain.threat.ThreatType;
import com.ctip.sdk.Confidence;
import com.ctip.sdk.RedistributionPolicy;
import com.ctip.sdk.Severity;
import com.ctip.sdk.SourceType;
import com.ctip.sdk.Tlp;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * {@link StixSchemaValidationTest} 的 M2 投影 fixture(§14.7:時間一律固定值)。
 *
 * <p>id 一律用<strong>版本與 variant 位元合法的 UUID</strong>:STIX 的 identifier 正規表示式
 * 只接受 v1–v5,而 {@code 00000000-…-0000a1} 這種測試常數不符合。正式環境的 id 來自
 * {@code gen_random_uuid()}(v4)或 {@code UUID.randomUUID()},拿假形狀的 id 驗 schema
 * 等於用一個現實不存在的輸入把檢查騙過去。
 */
final class StixProjectionFixtures {

    static final Instant T0 = Instant.parse("2026-08-01T00:00:00Z");

    private StixProjectionFixtures() {}

    static Threat malwareFamily() {
        return threat(ThreatType.MALWARE_FAMILY, "AgentTesla", Tlp.CLEAR, Set.of("Agent Tesla"));
    }

    static Threat threat(ThreatType type, String name, Tlp tlp, Set<String> aliases) {
        return Threat.create(new NewThreatCommand(
                new ThreatId(UUID.fromString("6f1e2d3c-4b5a-4c6d-8e7f-0a1b2c3d4e5f")),
                TenantId.PUBLIC,
                type,
                name,
                aliases,
                "Commodity infostealer distributed via phishing attachments.",
                Severity.HIGH,
                Confidence.of(70),
                tlp,
                Set.of("infostealer"),
                T0,
                T0.plusSeconds(86400)));
    }

    static SourceSnapshot sourceSnapshot() {
        return new SourceSnapshot(
                new SourceId(UUID.fromString("8f14e45f-ceea-467a-9575-9a1f8c0d4b6e")),
                SourceType.MOCK_OPENPHISH,
                "OpenPhish (Mock)",
                "https://openphish.example.test",
                Tlp.CLEAR,
                RedistributionPolicy.ATTRIBUTION_REQUIRED,
                new Reputation(70),
                true,
                true,
                Duration.ofHours(1),
                SourceHealth.initial(),
                null,
                null,
                0L);
    }
}
