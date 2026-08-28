package com.ctip.adapters.manual;

import com.ctip.sdk.IocHashType;
import com.ctip.sdk.IocType;
import com.ctip.sdk.RawThreatRecord;
import com.ctip.sdk.Severity;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 匯入 CSV 的解析(docs/spec/09-api.md §9.7 {@code POST /iocs/import},{@code Content-Type: text/csv})。
 *
 * <p>格式由本平台定義(§9.7 只寫「CSV」):
 *
 * <pre>
 * type,value,hashType,confidence,severity,validUntil,observedAt,tags,note
 * IPV4,203.0.113.5,,80,HIGH,,,internal-incident;phishing,observed in campaign
 * </pre>
 *
 * <ul>
 *   <li>必須有表頭;欄名不分大小寫,順序不拘,未知欄名一律拒絕(打錯欄名而被靜默忽略,
 *       等於使用者以為設定了卻沒有——與 §5.5 對稱性缺陷同一類)</li>
 *   <li>只有 {@code value} 是必填。{@code type} 留空由平台推斷(§7.2)</li>
 *   <li>{@code tags} 以 {@code ;} 分隔;{@code observedAt} 留空取提交時間</li>
 *   <li>支援 RFC 4180 的雙引號包夾與 {@code ""} 轉義;不支援跨行的引號內換行</li>
 * </ul>
 *
 * <p>逐列的內容正確性<strong>不在此判斷</strong>——型別推斷、正規化、私有 IP、長度、
 * allowlist 等一律由 pipeline 的既有 stage 負責(§8.3「不需要第二套資料品質邏輯」)。
 * 本類別只負責「文字 → {@link RawThreatRecord}」,連 {@code type} 都原樣交出。
 */
final class ManualSubmissionCsv {

    static final Set<String> COLUMNS =
            Set.of("type", "value", "hashtype", "confidence", "severity", "validuntil", "observedat", "tags", "note");

    private ManualSubmissionCsv() {}

    static List<RawThreatRecord> parse(String csv, Instant submittedAt) {
        if (csv == null || csv.isBlank()) {
            throw new IllegalArgumentException("CSV 內容為空");
        }
        List<String> lines = csv.lines().filter(line -> !line.isBlank()).toList();
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("CSV 內容為空");
        }
        List<String> header = splitRow(lines.get(0)).stream()
                .map(name -> name.trim().toLowerCase(Locale.ROOT))
                .toList();
        List<String> unknown =
                header.stream().filter(name -> !COLUMNS.contains(name)).toList();
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("CSV 表頭含未知欄名:" + unknown);
        }
        if (!header.contains("value")) {
            throw new IllegalArgumentException("CSV 表頭缺少必填欄 value");
        }
        List<RawThreatRecord> records = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            records.add(toRecord(header, splitRow(lines.get(i)), submittedAt, i));
        }
        return List.copyOf(records);
    }

    private static RawThreatRecord toRecord(
            List<String> header, List<String> cells, Instant submittedAt, int lineNumber) {
        String value = cell(header, cells, "value");
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("第 " + lineNumber + " 列的 value 為空");
        }
        return new RawThreatRecord(
                value,
                enumValue(IocType.class, cell(header, cells, "type"), "type", lineNumber),
                enumValue(IocHashType.class, cell(header, cells, "hashtype"), "hashType", lineNumber),
                instant(cell(header, cells, "observedat"), submittedAt, "observedAt", lineNumber),
                integer(cell(header, cells, "confidence"), lineNumber),
                enumValue(Severity.class, cell(header, cells, "severity"), "severity", lineNumber),
                instant(cell(header, cells, "validuntil"), null, "validUntil", lineNumber),
                tags(cell(header, cells, "tags")),
                note(cell(header, cells, "note")));
    }

    private static String cell(List<String> header, List<String> cells, String column) {
        int index = header.indexOf(column);
        if (index < 0 || index >= cells.size()) {
            return null;
        }
        String raw = cells.get(index).trim();
        return raw.isEmpty() ? null : raw;
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String raw, String column, int lineNumber) {
        if (raw == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("第 " + lineNumber + " 列的 " + column + " 不是合法值:" + raw);
        }
    }

    private static Instant instant(String raw, Instant fallback, String column, int lineNumber) {
        if (raw == null) {
            return fallback;
        }
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("第 " + lineNumber + " 列的 " + column + " 不是 ISO-8601 時間:" + raw);
        }
    }

    private static Integer integer(String raw, int lineNumber) {
        if (raw == null) {
            return null;
        }
        try {
            return Integer.valueOf(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("第 " + lineNumber + " 列的 confidence 不是整數:" + raw);
        }
    }

    private static Set<String> tags(String raw) {
        if (raw == null) {
            return Set.of();
        }
        return Arrays.stream(raw.split(";"))
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    /** note 進 raw_payload(indicator_sources.raw_payload),不另外開欄位。 */
    private static Map<String, Object> note(String raw) {
        return raw == null ? Map.of() : Map.of("note", raw);
    }

    /** RFC 4180 的最小子集:雙引號包夾、{@code ""} 轉義;不支援引號內換行。 */
    private static List<String> splitRow(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                cells.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        cells.add(current.toString());
        return cells;
    }
}
