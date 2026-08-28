package com.ctip.infrastructure.importing;

import com.ctip.adapters.manual.ManualSubmissionAdapter;
import com.ctip.application.ingestion.ImportFormat;
import com.ctip.application.port.AdapterRegistryPort;
import com.ctip.application.port.ClockPort;
import com.ctip.application.port.ImportPayloadParserPort;
import com.ctip.domain.stix.StixPatternParser;
import com.ctip.domain.stix.StixTlpMarkings;
import com.ctip.sdk.FetchContext;
import com.ctip.sdk.RawThreatRecord;
import com.ctip.sdk.Severity;
import com.ctip.sdk.SourceType;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link ImportPayloadParserPort} 的實作(docs/spec/09-api.md §9.7)。
 *
 * <p>兩種格式走不同路徑,理由不對稱但有意為之:
 * <ul>
 *   <li><strong>CSV</strong> 交給 {@code ManualSubmissionAdapter.fetch()}——§8.3 明定手動提交批次
 *       由該 adapter 從 {@code FetchContext.config} 取出,CSV 只需 SDK 型別,adapter 模組即可自足</li>
 *   <li><strong>STIX bundle</strong> 在此解——它需要 JSON 解析器,而 {@code ctip-adapters}
 *       刻意不依賴任何 JSON 函式庫(「只認識 SDK 契約」),{@code ctip-core} 亦無 JSON 相依。
 *       Boot 4 的 Jackson 只存在於 ctip-app(ADR 0023)</li>
 * </ul>
 *
 * <p>兩者都只做「位元組 → {@link RawThreatRecord}」;資料品質全在 pipeline(§8.3)。
 */
@Component
class ImportPayloadParser implements ImportPayloadParserPort {

    /** 單次解碼的筆數硬上限(ImportService 另有依方案的檢查);超過即視為檔案過大。 */
    private static final int MAX_RECORDS = 1_000_000;

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final AdapterRegistryPort adapters;
    private final ClockPort clock;

    ImportPayloadParser(AdapterRegistryPort adapters, ClockPort clock) {
        this.adapters = adapters;
        this.clock = clock;
    }

    @Override
    public List<RawThreatRecord> parse(ImportFormat format, String payload) {
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("匯入內容為空");
        }
        Instant submittedAt = clock.now();
        return switch (format) {
            case CSV -> parseCsv(payload, submittedAt);
            case STIX_BUNDLE -> parseBundle(payload, submittedAt);
        };
    }

    private List<RawThreatRecord> parseCsv(String payload, Instant submittedAt) {
        var adapter =
                adapters.find(SourceType.MANUAL).orElseThrow(() -> new IllegalStateException("沒有註冊 MANUAL adapter"));
        return adapter.fetch(new FetchContext(
                        null,
                        null,
                        Map.of(
                                ManualSubmissionAdapter.CONFIG_FORMAT,
                                ManualSubmissionAdapter.FORMAT_CSV,
                                ManualSubmissionAdapter.CONFIG_PAYLOAD,
                                payload,
                                ManualSubmissionAdapter.CONFIG_SUBMITTED_AT,
                                submittedAt.toString()),
                        MAX_RECORDS))
                .records();
    }

    /**
     * STIX 2.1 bundle:只取 {@code type = "indicator"} 的物件,值由 §7.8.3 的固定 pattern 反解。
     * 認不得的 pattern 直接略過會讓使用者以為匯入成功——改為原樣交出 pattern 字串,
     * 由 pipeline 的 ValidateStage 記為拒絕並附理由(§7.3「不得靜默丟棄」)。
     */
    private List<RawThreatRecord> parseBundle(String payload, Instant submittedAt) {
        JsonNode bundle = readTree(payload);
        if (!"bundle".equals(bundle.path("type").asString(""))) {
            throw new IllegalArgumentException(
                    "不是 STIX bundle:type = " + bundle.path("type").asString(""));
        }
        List<RawThreatRecord> records = new ArrayList<>();
        for (JsonNode object : bundle.path("objects")) {
            if (!"indicator".equals(object.path("type").asString(""))) {
                continue;
            }
            if (hasRedMarking(object)) {
                throw new IllegalArgumentException("bundle 含 TLP:RED 物件;RED 不進入平台(07)");
            }
            records.add(toRecord(object, submittedAt));
            if (records.size() > MAX_RECORDS) {
                throw new IllegalArgumentException("bundle 超過解碼上限 " + MAX_RECORDS + " 筆");
            }
        }
        if (records.isEmpty()) {
            throw new IllegalArgumentException("bundle 沒有任何 indicator 物件");
        }
        return List.copyOf(records);
    }

    private static RawThreatRecord toRecord(JsonNode object, Instant submittedAt) {
        String pattern = object.path("pattern").asString("");
        var parsed = StixPatternParser.parse(pattern);
        return new RawThreatRecord(
                parsed.map(StixPatternParser.ParsedPattern::value).orElse(pattern),
                parsed.map(StixPatternParser.ParsedPattern::type).orElse(null),
                parsed.map(StixPatternParser.ParsedPattern::hashType).orElse(null),
                instant(object, "valid_from", submittedAt),
                object.hasNonNull("confidence") ? object.path("confidence").asInt() : null,
                severity(object),
                instant(object, "valid_until", null),
                labels(object),
                // revoked 是 §7.5 撤回語意的輸入(ParseStage 讀它);其餘欄位落 raw_payload
                object.path("revoked").asBoolean(false) ? Map.of("revoked", true) : Map.of());
    }

    private static boolean hasRedMarking(JsonNode object) {
        for (JsonNode ref : object.path("object_marking_refs")) {
            if (StixTlpMarkings.RED_ID.equals(ref.asString(""))) {
                return true;
            }
        }
        return false;
    }

    /** STIX 沒有 severity 欄位;本平台輸出時不寫,匯入時亦不猜——只認自家的 labels 慣例。 */
    private static Severity severity(JsonNode object) {
        for (JsonNode label : object.path("labels")) {
            String value = label.asString("").toUpperCase(Locale.ROOT);
            for (Severity severity : Severity.values()) {
                if (severity.name().equals(value)) {
                    return severity;
                }
            }
        }
        return null;
    }

    private static Set<String> labels(JsonNode object) {
        Set<String> labels = new LinkedHashSet<>();
        for (JsonNode label : object.path("labels")) {
            String value = label.asString("").trim();
            if (!value.isEmpty()) {
                labels.add(value);
            }
        }
        return labels;
    }

    private static Instant instant(JsonNode object, String field, Instant fallback) {
        String raw = object.path(field).asString("");
        if (raw.isEmpty()) {
            return fallback;
        }
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("欄位 " + field + " 不是 ISO-8601 時間:" + raw);
        }
    }

    private static JsonNode readTree(String payload) {
        try {
            return MAPPER.readTree(payload);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("不是合法的 JSON:" + e.getOriginalMessage());
        }
    }
}
