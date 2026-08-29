package com.ctip.domain.stix;

import com.ctip.domain.source.SourceSnapshot;
import com.ctip.domain.tenant.TenantId;
import com.ctip.sdk.Tlp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Source → STIX 2.1 {@code identity} 的映射
 * (docs/spec/07-domain-intel.md §7.8.7;id 規則由 ADR 0020 定為 {@code identity--{sourceId}})。
 *
 * <p>情資提供方的身分本身不是情資:owner 為 public tenant、TLP 固定 {@code CLEAR},
 * 這樣它才能與任何 TLP 的物件放進同一個 bundle 而不影響過濾結果。
 */
public final class StixIdentityProjector {

    private StixIdentityProjector() {}

    public static String stixId(SourceSnapshot source) {
        return "identity--" + source.id().value();
    }

    public static StixProjection project(SourceSnapshot source, Instant created, Instant modified) {
        return new StixProjection(
                stixId(source),
                "identity",
                TenantId.PUBLIC,
                null,
                null,
                Tlp.CLEAR,
                created,
                modified,
                content(source, created, modified));
    }

    private static Map<String, Object> content(SourceSnapshot source, Instant created, Instant modified) {
        Map<String, Object> stix = new LinkedHashMap<>();
        stix.put("type", "identity");
        stix.put("spec_version", "2.1");
        stix.put("id", stixId(source));
        stix.put("created", StixTimestamps.format(created));
        stix.put("modified", StixTimestamps.format(modified));
        stix.put("name", source.displayName());
        stix.put("identity_class", "organization");
        stix.put("sectors", List.of("technology"));
        stix.put("labels", List.of("source-type:" + source.sourceType().name()));
        if (source.homepageUrl() != null) {
            stix.put(
                    "external_references",
                    List.of(Map.of("source_name", source.displayName(), "url", source.homepageUrl())));
        }
        stix.put("object_marking_refs", List.of(StixTlpMarkings.markingId(Tlp.CLEAR)));
        return stix;
    }
}
