package com.ctip.adapters.manual;

import com.ctip.sdk.FetchContext;
import com.ctip.sdk.FetchResult;
import com.ctip.sdk.IocType;
import com.ctip.sdk.RawThreatRecord;
import com.ctip.sdk.RedistributionPolicy;
import com.ctip.sdk.SourceMetadata;
import com.ctip.sdk.SourceType;
import com.ctip.sdk.ThreatSourceAdapter;
import com.ctip.sdk.Tlp;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 手動提交與檔案匯入的來源 adapter(docs/spec/08-ingestion-sdk.md §8.3 {@code ManualSubmissionAdapter})。
 *
 * <p>手動提交與匯入<strong>複用同一條 pipeline</strong>,因此自動獲得完整的驗證、正規化、
 * 去重、合併與稽核——不需要第二套資料品質邏輯。本 adapter 的職責只有兩件:
 * 宣告來源契約({@code MANUAL} / {@code INTERNAL_ONLY} / 預設 {@code AMBER}),
 * 以及把提交批次解碼成 {@link RawThreatRecord}。
 *
 * <p>{@code fetch()} 依 §8.3 從 {@link FetchContext#config()} 取出待處理的提交批次:
 * <ul>
 *   <li>{@code format} —— 目前只支援 {@code CSV}(§9.7 的另一種格式 STIX bundle 需要 JSON 解析,
 *       而本模組刻意不依賴任何 JSON 函式庫;bundle 於 ctip-app 解碼,見 ADR 0023)</li>
 *   <li>{@code payload} —— CSV 文字,格式見 {@link ManualSubmissionCsv}</li>
 *   <li>{@code submittedAt} —— ISO-8601;CSV 未給 {@code observedAt} 的列以此為觀測時間。
 *       由呼叫端以 {@code ClockPort} 提供,adapter 本身保持確定性(同一 FetchContext 必回相同結果)</li>
 * </ul>
 *
 * <p>本來源 {@code syncable = false}(V4 種子),不參與排程與健康狀態轉換。
 */
public final class ManualSubmissionAdapter implements ThreatSourceAdapter {

    public static final String CONFIG_FORMAT = "format";
    public static final String CONFIG_PAYLOAD = "payload";
    public static final String CONFIG_SUBMITTED_AT = "submittedAt";
    public static final String FORMAT_CSV = "CSV";

    @Override
    public SourceType sourceType() {
        return SourceType.MANUAL;
    }

    /** 與 V4 種子(V4__seed_sources.sql)對齊;{@code recommendedInterval} 為 null——本來源不排程。 */
    @Override
    public SourceMetadata metadata() {
        return new SourceMetadata(
                "Manual Submission",
                "使用者手動提交與檔案匯入(排除於排程與健康狀態轉換)",
                null,
                EnumSet.allOf(IocType.class),
                Tlp.AMBER,
                RedistributionPolicy.INTERNAL_ONLY,
                null,
                false);
    }

    @Override
    public FetchResult fetch(FetchContext context) {
        if (context.maxRecords() <= 0) {
            throw new IllegalArgumentException("maxRecords 必須為正數:" + context.maxRecords());
        }
        String format = context.config().get(CONFIG_FORMAT);
        if (!FORMAT_CSV.equals(format)) {
            throw new IllegalArgumentException("ManualSubmissionAdapter 只解碼 CSV,收到:" + format);
        }
        Instant submittedAt = Instant.parse(context.config().get(CONFIG_SUBMITTED_AT));
        List<RawThreatRecord> parsed =
                ManualSubmissionCsv.parse(context.config().get(CONFIG_PAYLOAD), submittedAt);
        boolean hasMore = parsed.size() > context.maxRecords();
        List<RawThreatRecord> records = hasMore ? List.copyOf(parsed.subList(0, context.maxRecords())) : parsed;
        return new FetchResult(records, null, hasMore);
    }

    /** CSV 的合法欄名(供呼叫端在解析前檢查表頭並回報明確錯誤)。 */
    public static Set<String> csvColumns() {
        return ManualSubmissionCsv.COLUMNS;
    }
}
