package com.ctip.adapters.manual;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ctip.sdk.FetchContext;
import com.ctip.sdk.FetchResult;
import com.ctip.sdk.IocHashType;
import com.ctip.sdk.IocType;
import com.ctip.sdk.RedistributionPolicy;
import com.ctip.sdk.Severity;
import com.ctip.sdk.SourceType;
import com.ctip.sdk.Tlp;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * 手動提交 adapter 與其 CSV 解碼(docs/spec/08-ingestion-sdk.md §8.3、09 §9.7)。
 *
 * <p>解碼只負責「文字 → RawThreatRecord」;值的正確性由 pipeline 判斷,
 * 因此這裡刻意<strong>不</strong>驗證 IOC 本身合不合法。
 */
@Tag("unit")
class ManualSubmissionAdapterTest {

    private static final Instant SUBMITTED_AT = Instant.parse("2026-08-28T09:00:00Z");

    private final ManualSubmissionAdapter adapter = new ManualSubmissionAdapter();

    private FetchResult parse(String csv) {
        return adapter.fetch(new FetchContext(
                null,
                null,
                Map.of(
                        ManualSubmissionAdapter.CONFIG_FORMAT,
                        ManualSubmissionAdapter.FORMAT_CSV,
                        ManualSubmissionAdapter.CONFIG_PAYLOAD,
                        csv,
                        ManualSubmissionAdapter.CONFIG_SUBMITTED_AT,
                        SUBMITTED_AT.toString()),
                1000));
    }

    /** metadata 必須與 V4 種子一致——§8.3 的三個契約值就是靠它進 indicator_sources。 */
    @Test
    void metadataMatchesTheSeededSource() {
        assertThat(adapter.sourceType()).isEqualTo(SourceType.MANUAL);
        assertThat(adapter.metadata().defaultTlp()).isEqualTo(Tlp.AMBER);
        assertThat(adapter.metadata().redistributionPolicy()).isEqualTo(RedistributionPolicy.INTERNAL_ONLY);
        assertThat(adapter.metadata().supportedIocTypes()).containsExactlyInAnyOrder(IocType.values());
    }

    @Test
    void parsesAllColumns() {
        FetchResult result = parse("""
                type,value,hashType,confidence,severity,validUntil,observedAt,tags,note
                FILE_HASH,d41d8cd98f00b204e9800998ecf8427e,MD5,90,CRITICAL,2027-01-01T00:00:00Z,\
                2026-08-01T00:00:00Z,a;b,seen in sample
                """);

        assertThat(result.records()).singleElement().satisfies(record -> {
            assertThat(record.rawValue()).isEqualTo("d41d8cd98f00b204e9800998ecf8427e");
            assertThat(record.declaredType()).isEqualTo(IocType.FILE_HASH);
            assertThat(record.declaredHashType()).isEqualTo(IocHashType.MD5);
            assertThat(record.sourceConfidence()).isEqualTo(90);
            assertThat(record.sourceSeverity()).isEqualTo(Severity.CRITICAL);
            assertThat(record.validUntil()).isEqualTo(Instant.parse("2027-01-01T00:00:00Z"));
            assertThat(record.observedAt()).isEqualTo(Instant.parse("2026-08-01T00:00:00Z"));
            assertThat(record.tags()).containsExactlyInAnyOrder("a", "b");
            assertThat(record.rawPayload()).containsEntry("note", "seen in sample");
        });
    }

    /** 只有 value 必填;type 留空由平台推斷(§7.2),observedAt 留空取提交時間。 */
    @Test
    void optionalColumnsMayBeOmitted() {
        FetchResult result = parse("value\nexample.org\n");

        assertThat(result.records()).singleElement().satisfies(record -> {
            assertThat(record.declaredType()).isNull();
            assertThat(record.observedAt()).isEqualTo(SUBMITTED_AT);
            assertThat(record.tags()).isEmpty();
        });
    }

    /** 引號內的逗號不得被當成分隔;{@code ""} 為跳脫的引號(RFC 4180 子集)。 */
    @Test
    void quotedFieldsKeepCommasAndEscapedQuotes() {
        FetchResult result = parse("value,note\nexample.org,\"a, b and \"\"c\"\"\"\n");

        assertThat(result.records())
                .singleElement()
                .satisfies(record -> assertThat(record.rawPayload()).containsEntry("note", "a, b and \"c\""));
    }

    /**
     * 未知欄名一律拒絕。靜默忽略打錯的欄名,使用者會以為 confidence 設定了、實際沒有
     * ——與「寫進 .env 卻到不了容器」同一類的無聲失效。
     */
    @Test
    void unknownColumnIsRejected() {
        assertThatThrownBy(() -> parse("value,severty\nexample.org,HIGH\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("severty");
    }

    @Test
    void missingValueColumnIsRejected() {
        assertThatThrownBy(() -> parse("type,confidence\nDOMAIN,50\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("value");
    }

    @Test
    void malformedCellsAreReportedWithTheirLineNumber() {
        assertThatThrownBy(() -> parse("value,confidence\nexample.org,not-a-number\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("第 1 列");
    }

    /** 只解 CSV;STIX bundle 需要 JSON 解析器,不在本模組(ADR 0023)。 */
    @Test
    void onlyCsvIsDecodedHere() {
        assertThatThrownBy(() -> adapter.fetch(new FetchContext(
                        null,
                        null,
                        Map.of(
                                ManualSubmissionAdapter.CONFIG_FORMAT, "STIX_BUNDLE",
                                ManualSubmissionAdapter.CONFIG_PAYLOAD, "{}",
                                ManualSubmissionAdapter.CONFIG_SUBMITTED_AT, SUBMITTED_AT.toString()),
                        10)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** maxRecords 是硬上限:超過即 hasMore,呼叫端才有機會判定「檔案過大」。 */
    @Test
    void maxRecordsIsHonoured() {
        FetchResult result = adapter.fetch(new FetchContext(
                null,
                null,
                Map.of(
                        ManualSubmissionAdapter.CONFIG_FORMAT,
                        ManualSubmissionAdapter.FORMAT_CSV,
                        ManualSubmissionAdapter.CONFIG_PAYLOAD,
                        "value\na.example.org\nb.example.org\n",
                        ManualSubmissionAdapter.CONFIG_SUBMITTED_AT,
                        SUBMITTED_AT.toString()),
                1));

        assertThat(result.records()).hasSize(1);
        assertThat(result.hasMore()).isTrue();
    }
}
