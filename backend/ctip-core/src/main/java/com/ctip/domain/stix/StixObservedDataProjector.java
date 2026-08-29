package com.ctip.domain.stix;

import com.ctip.domain.indicator.IndicatorSnapshot;
import com.ctip.domain.indicator.IndicatorSourceSnapshot;
import com.ctip.domain.indicator.IocValue;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * IndicatorSource → STIX 2.1 {@code observed-data} 的映射
 * (docs/spec/07-domain-intel.md §7.8.7 對照表;來源 domain 物件是「單一來源的一次觀測」)。
 *
 * <p>一個 Indicator 有 N 個來源記錄就有 N 筆 observed-data;id 由
 * {@code (indicatorId, sourceId)} 決定,重投影是 UPSERT。
 *
 * <p>{@code objects} 內嵌 SCO 而非 {@code object_refs}:平台沒有獨立持久化 SCO,
 * 而 schema 要求 {@code objects} 與 {@code object_refs} 至少有一個——給 {@code object_refs}
 * 會指向不存在的物件。SCO 的型別與值對應 §7.8.3 的 pattern 模板(同一組型別對照)。
 */
public final class StixObservedDataProjector {

    private StixObservedDataProjector() {}

    public static String stixId(IndicatorSnapshot snapshot, IndicatorSourceSnapshot record) {
        return "observed-data--"
                + StixIds.deterministic("observed-data:" + snapshot.id().value() + ":"
                        + record.sourceId().value());
    }

    public static StixProjection project(
            IndicatorSnapshot snapshot, IndicatorSourceSnapshot record, Instant created, Instant modified) {
        return new StixProjection(
                stixId(snapshot, record),
                "observed-data",
                snapshot.ownerTenantId(),
                snapshot.id(),
                null,
                record.sourceTlp(),
                created,
                modified,
                content(snapshot, record, created, modified));
    }

    private static Map<String, Object> content(
            IndicatorSnapshot s, IndicatorSourceSnapshot record, Instant created, Instant modified) {
        Map<String, Object> stix = new LinkedHashMap<>();
        stix.put("type", "observed-data");
        stix.put("spec_version", "2.1");
        stix.put("id", stixId(s, record));
        stix.put("created", StixTimestamps.format(created));
        stix.put("modified", StixTimestamps.format(modified));
        stix.put("first_observed", StixTimestamps.format(record.sourceFirstSeen()));
        stix.put("last_observed", StixTimestamps.format(record.sourceLastSeen()));
        // number_observed 的 schema 下限是 1:reportCount 理論上恆 >= 1,但重建路徑不保證,故夾住
        stix.put("number_observed", Math.max(1, record.reportCount()));
        stix.put("objects", Map.of("0", observable(s.value())));
        // sourceConfidence 是 nullable(來源沒說就是 null);STIX 的 confidence 是選填,省略即可
        if (record.sourceConfidence() != null) {
            stix.put("confidence", record.sourceConfidence().value());
        }
        stix.put("object_marking_refs", List.of(StixTlpMarkings.markingId(record.sourceTlp())));
        return stix;
    }

    /** 六種 IocType 對應的 SCO(型別對照同 §7.8.3);id 由型別 + 正規化值決定,穩定且可去重。 */
    private static Map<String, Object> observable(IocValue value) {
        Map<String, Object> sco = new LinkedHashMap<>();
        String type = observableType(value);
        sco.put("type", type);
        sco.put("id", type + "--" + StixIds.deterministic(type + ":" + value.normalized()));
        if (value.hashType() == null) {
            sco.put("value", value.normalized());
        } else {
            sco.put("hashes", Map.of(StixPatternBuilder.hashKey(value.hashType()), value.normalized()));
        }
        return sco;
    }

    private static String observableType(IocValue value) {
        return switch (value.type()) {
            case IPV4 -> "ipv4-addr";
            case IPV6 -> "ipv6-addr";
            case DOMAIN -> "domain-name";
            case URL -> "url";
            case EMAIL -> "email-addr";
            case FILE_HASH -> "file";
        };
    }
}
