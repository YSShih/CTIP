package com.ctip.domain.stix;

import com.ctip.domain.threat.ExternalReference;
import com.ctip.domain.threat.ThreatSnapshot;
import com.ctip.domain.threat.ThreatStatus;
import com.ctip.domain.threat.ThreatType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Threat → STIX 2.1 {@code malware} / {@code attack-pattern} 的映射
 * (docs/spec/07-domain-intel.md §7.8.7 對照表;體例同 §7.8.2)。
 *
 * <p>M2 只投影 {@code MALWARE_FAMILY} 與 {@code ATTACK_PATTERN}(§7.8.1):
 * {@code CAMPAIGN}／{@code THREAT_ACTOR} 在 STIX 有對應型別但本版不投影,
 * {@code PHISHING_KIT} 沒有標準型別。呼叫端必須先問 {@code Threat.hasStixProjection()}。
 *
 * <p>{@code first_seen}／{@code last_seen} 只出現在 {@code malware}——
 * {@code attack-pattern} 的 schema 沒有這兩個屬性,硬塞會產生非標準物件。
 */
public final class StixThreatProjector {

    private StixThreatProjector() {}

    /** {@code malware--{threats.id}} / {@code attack-pattern--{threats.id}}(§7.8.2 的 id 規則)。 */
    public static String stixId(ThreatSnapshot snapshot) {
        return stixType(snapshot.type()) + "--" + snapshot.id().value();
    }

    public static StixProjection project(ThreatSnapshot snapshot, Instant created, Instant modified) {
        return new StixProjection(
                stixId(snapshot),
                stixType(snapshot.type()),
                snapshot.ownerTenantId(),
                null,
                snapshot.id(),
                snapshot.tlp(),
                created,
                modified,
                content(snapshot, created, modified));
    }

    private static String stixType(ThreatType type) {
        return switch (type) {
            case MALWARE_FAMILY -> "malware";
            case ATTACK_PATTERN -> "attack-pattern";
            case CAMPAIGN, THREAT_ACTOR, PHISHING_KIT ->
                throw new IllegalArgumentException("M2 不投影此 ThreatType(§7.8.1):" + type);
        };
    }

    private static Map<String, Object> content(ThreatSnapshot s, Instant created, Instant modified) {
        boolean malware = s.type() == ThreatType.MALWARE_FAMILY;
        Map<String, Object> stix = new LinkedHashMap<>();
        stix.put("type", stixType(s.type()));
        stix.put("spec_version", "2.1");
        stix.put("id", stixId(s));
        stix.put("created", StixTimestamps.format(created));
        stix.put("modified", StixTimestamps.format(modified));
        stix.put("name", s.name());
        if (s.description() != null) {
            stix.put("description", s.description());
        }
        if (malware) {
            // is_family 是 malware 唯一的必填屬性;來源是 ThreatType 本身(MALWARE_FAMILY)
            stix.put("is_family", true);
            stix.put("first_seen", StixTimestamps.format(s.firstSeen()));
            stix.put("last_seen", StixTimestamps.format(s.lastSeen()));
        }
        if (!s.aliases().isEmpty()) {
            // schema 的 minItems 為 1:沒有別名時整個屬性必須省略,不得給空陣列
            stix.put("aliases", s.aliases().stream().sorted().toList());
        }
        stix.put("confidence", s.confidence().value());
        stix.put("labels", labels(s));
        if (s.status() == ThreatStatus.RETIRED) {
            stix.put("revoked", true);
        }
        stix.put("object_marking_refs", List.of(StixTlpMarkings.markingId(s.tlp())));
        if (!s.externalReferences().isEmpty()) {
            stix.put("external_references", externalReferences(s));
        }
        return stix;
    }

    private static List<String> labels(ThreatSnapshot s) {
        List<String> labels = new ArrayList<>();
        labels.add("severity:" + s.severity().name());
        labels.add("status:" + s.status().name());
        labels.addAll(s.tags().stream().sorted().toList());
        return labels;
    }

    private static List<Map<String, Object>> externalReferences(ThreatSnapshot s) {
        List<Map<String, Object>> references = new ArrayList<>();
        for (ExternalReference reference : s.externalReferences()) {
            Map<String, Object> mapped = new LinkedHashMap<>();
            mapped.put("source_name", reference.sourceName());
            if (reference.externalId() != null) {
                mapped.put("external_id", reference.externalId());
            }
            if (reference.url() != null) {
                mapped.put("url", reference.url());
            }
            if (reference.description() != null) {
                mapped.put("description", reference.description());
            }
            references.add(mapped);
        }
        return references;
    }
}
