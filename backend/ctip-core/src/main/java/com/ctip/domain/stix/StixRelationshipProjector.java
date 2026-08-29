package com.ctip.domain.stix;

import com.ctip.domain.threat.ThreatIndicatorLink;
import com.ctip.domain.threat.ThreatSnapshot;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ThreatIndicatorLink → STIX 2.1 {@code relationship} 的映射
 * (docs/spec/07-domain-intel.md §7.8.7;落庫於 {@code stix_relationships},04 表 9)。
 *
 * <p>方向依 STIX 2.1 的關聯詞彙:{@code indicator --indicates--> malware|attack-pattern}。
 * 反過來寫(threat indicates indicator)不是標準關聯,匯入端會看不懂。
 * {@code IndicatorRole} 沒有對應的 STIX 屬性,放進 {@code description} 保留語意。
 *
 * <p>{@link #project} 產生落庫的三元組,{@link #content} 產生對外的 JSON——
 * 表 9 沒有 content 欄,JSON 一律於讀取時由同一個投影規則重建。
 */
public final class StixRelationshipProjector {

    private static final String INDICATES = "indicates";

    private StixRelationshipProjector() {}

    public static String stixId(String sourceRef, String targetRef) {
        return "relationship--"
                + StixIds.deterministic("relationship:" + INDICATES + ":" + sourceRef + ":" + targetRef);
    }

    public static String sourceRef(ThreatIndicatorLink link) {
        return "indicator--" + link.indicatorId().value();
    }

    public static StixRelationship project(
            ThreatSnapshot threat, ThreatIndicatorLink link, Instant created, Instant modified) {
        String sourceRef = sourceRef(link);
        String targetRef = StixThreatProjector.stixId(threat);
        return new StixRelationship(
                stixId(sourceRef, targetRef),
                INDICATES,
                sourceRef,
                targetRef,
                threat.ownerTenantId(),
                threat.tlp(),
                created,
                modified);
    }

    /** 對外的 JSON;{@code created}/{@code modified} 取自落庫的三元組,重建不會漂移。 */
    public static Map<String, Object> content(
            ThreatSnapshot threat, ThreatIndicatorLink link, Instant created, Instant modified) {
        String sourceRef = sourceRef(link);
        String targetRef = StixThreatProjector.stixId(threat);
        Map<String, Object> stix = new LinkedHashMap<>();
        stix.put("type", "relationship");
        stix.put("spec_version", "2.1");
        stix.put("id", stixId(sourceRef, targetRef));
        stix.put("created", StixTimestamps.format(created));
        stix.put("modified", StixTimestamps.format(modified));
        stix.put("relationship_type", INDICATES);
        stix.put("source_ref", sourceRef);
        stix.put("target_ref", targetRef);
        stix.put(
                "description",
                "Indicator role within the threat: " + link.role().name());
        stix.put("start_time", StixTimestamps.format(link.addedAt()));
        stix.put("object_marking_refs", List.of(StixTlpMarkings.markingId(threat.tlp())));
        return stix;
    }
}
