package com.ctip.domain.stix;

import com.ctip.domain.indicator.IndicatorSnapshot;
import com.ctip.domain.indicator.IndicatorSourceSnapshot;
import com.ctip.domain.indicator.IndicatorStatus;
import com.ctip.domain.indicator.SourceRecordStatus;
import com.ctip.domain.source.SourceId;
import com.ctip.sdk.RedistributionPolicy;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Indicator → STIX 2.1 indicator 物件的映射(docs/spec/07-domain-intel.md §7.8.2 強制對照表)。
 * 手寫 builder(phase-08 明列的唯一例外);屬性順序依對照表,時間為 ISO-8601 毫秒精度 Z 結尾。
 * created/modified 由呼叫端提供(stage 於 Persist 前執行,DB 的 created_at/updated_at 尚不存在,
 * 以「既有投影的 created、當下時間為 modified」近似;見 ADR 0005)。
 */
public final class StixIndicatorProjector {

    private static final DateTimeFormatter STIX_TIMESTAMP =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);
    private static final int NAME_MAX_LENGTH = 255;

    private StixIndicatorProjector() {}

    public static String stixId(IndicatorSnapshot snapshot) {
        return "indicator--" + snapshot.id().value();
    }

    /** sourceNames:external_references 需要的來源顯示名稱(sourceId → displayName)。 */
    public static StixProjection project(
            IndicatorSnapshot snapshot, Map<SourceId, String> sourceNames, Instant created, Instant modified) {
        return new StixProjection(
                stixId(snapshot),
                "indicator",
                snapshot.ownerTenantId(),
                snapshot.id(),
                snapshot.tlp(),
                created,
                modified,
                content(snapshot, sourceNames, created, modified));
    }

    private static Map<String, Object> content(
            IndicatorSnapshot s, Map<SourceId, String> sourceNames, Instant created, Instant modified) {
        Map<String, Object> stix = new LinkedHashMap<>();
        stix.put("type", "indicator");
        stix.put("spec_version", "2.1");
        stix.put("id", stixId(s));
        stix.put("created", STIX_TIMESTAMP.format(created));
        stix.put("modified", STIX_TIMESTAMP.format(modified));
        stix.put("pattern", StixPatternBuilder.pattern(s.value()));
        stix.put("pattern_type", "stix");
        stix.put("pattern_version", "2.1");
        stix.put("valid_from", STIX_TIMESTAMP.format(s.firstSeen()));
        if (s.validUntil() != null) {
            stix.put("valid_until", STIX_TIMESTAMP.format(s.validUntil()));
        }
        stix.put("name", truncate(s.value().type().name() + ": " + s.value().normalized()));
        stix.put(
                "description",
                "Aggregated from " + activeSourceCount(s) + " active source(s). Threat score: " + s.score() + ".");
        stix.put(
                "indicator_types",
                s.status() == IndicatorStatus.FALSE_POSITIVE ? List.of("benign") : List.of("malicious-activity"));
        stix.put("confidence", s.confidence().value());
        stix.put("labels", labels(s));
        if (s.status() == IndicatorStatus.REVOKED) {
            stix.put("revoked", true);
        }
        stix.put("object_marking_refs", List.of(StixTlpMarkings.markingId(s.tlp())));
        List<Map<String, Object>> references = externalReferences(s, sourceNames);
        if (!references.isEmpty()) {
            stix.put("external_references", references);
        }
        return stix;
    }

    private static List<String> labels(IndicatorSnapshot s) {
        List<String> labels = new ArrayList<>();
        labels.add("severity:" + s.severity().name());
        labels.add("score:" + s.score());
        labels.addAll(s.tags().stream().sorted().toList());
        return labels;
    }

    /** 僅 ATTRIBUTION_REQUIRED / PUBLIC_REDISTRIBUTABLE 的來源附上標註(§7.8.2、§7.9)。 */
    private static List<Map<String, Object>> externalReferences(
            IndicatorSnapshot s, Map<SourceId, String> sourceNames) {
        List<Map<String, Object>> references = new ArrayList<>();
        for (IndicatorSourceSnapshot record : s.sources()) {
            if (record.redistributionPolicy() == RedistributionPolicy.ATTRIBUTION_REQUIRED
                    || record.redistributionPolicy() == RedistributionPolicy.PUBLIC_REDISTRIBUTABLE) {
                String name = sourceNames.get(record.sourceId());
                if (name != null) {
                    // STIX external-reference 要求 source_name 之外至少有 description/url/external_id 之一
                    references.add(Map.of(
                            "source_name",
                            name,
                            "description",
                            "Threat intelligence source that reported this indicator."));
                }
            }
        }
        return references;
    }

    private static long activeSourceCount(IndicatorSnapshot s) {
        return s.sources().stream()
                .filter(r -> r.status() == SourceRecordStatus.ACTIVE)
                .count();
    }

    private static String truncate(String name) {
        if (name.length() <= NAME_MAX_LENGTH) {
            return name;
        }
        // 直接 substring 會在 astral 字元(surrogate pair)恰好跨邊界時切出半個 char,
        // 產生無效的 UTF-16 序列。若最後保留的 char 是高代理,其低代理必然落在被丟棄的一側,
        // 退一格讓邊界落在完整字元上(ADR 0015)
        int end = Character.isHighSurrogate(name.charAt(NAME_MAX_LENGTH - 1)) ? NAME_MAX_LENGTH - 1 : NAME_MAX_LENGTH;
        return name.substring(0, end);
    }
}
