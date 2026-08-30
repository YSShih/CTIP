package com.ctip.sdk.example;

import com.ctip.sdk.FetchContext;
import com.ctip.sdk.FetchResult;
import com.ctip.sdk.IocType;
import com.ctip.sdk.RawThreatRecord;
import com.ctip.sdk.RedistributionPolicy;
import com.ctip.sdk.Severity;
import com.ctip.sdk.SourceMetadata;
import com.ctip.sdk.SourceType;
import com.ctip.sdk.ThreatSourceAdapter;
import com.ctip.sdk.Tlp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 第三方 adapter 的完整可編譯範例(docs/spec/08-ingestion-sdk.md §8.1「SDK 文件」;
 * 逐步說明見 docs/development/plugin-sdk.md)。
 *
 * <p>虛構來源 ExampleFeed 提供一份以 TAB 分隔的純文字清單,每行一筆:
 *
 * <pre>
 * value &lt;TAB&gt; type &lt;TAB&gt; observedAt &lt;TAB&gt; confidence &lt;TAB&gt; severity &lt;TAB&gt; tags(逗號分隔,可空)
 * </pre>
 *
 * <p>範例示範六件事:宣告 metadata(含 TLP 與再散布政策)、從 {@link FetchContext#config()}
 * 取憑證、以 {@code since} 做增量、以 {@code cursor} 續抓、遵守 {@code maxRecords} 上限,
 * 以及把來源格式解析成 {@link RawThreatRecord}——<strong>不做正規化</strong>
 * (正規化是平台 pipeline 的 stage 3,adapter 只負責忠實轉錄原始值)。
 *
 * <p>取得 feed 內容的方式抽成 {@link FeedClient},HTTP 呼叫因此可以在測試中替換成固定字串
 * ({@link ExampleAdapterTest});真實 adapter 在這裡放自己的 HTTP client。
 *
 * <p><strong>關於 {@code sourceType()}:</strong>真實的第三方來源必須在
 * {@link SourceType} 新增自己的成員(SDK 的 minor 變更,見該列舉的 javadoc),
 * 並在 {@code sources} 表有對應的一列。本範例位於 SDK 的<strong>測試原始碼</strong>、
 * 永不註冊成 bean,因此沿用既有成員即可——為了一份範例而在列舉留下沒有 {@code sources} 列的
 * 成員,會違反規則 16(不得留下永不可達的列舉值)。
 */
public final class ExampleThreatSourceAdapter implements ThreatSourceAdapter {

    /** 來源憑證在 {@code sources.config} 只存環境變數名稱,解析後才進到 {@link FetchContext#config()}。 */
    public static final String API_KEY = "exampleFeedApiKey";

    private static final int PAGE_SIZE = 50;
    private static final int FIELD_COUNT = 6;

    /** 取得 feed 原文;真實 adapter 在此發 HTTP 請求。 */
    @FunctionalInterface
    public interface FeedClient {
        String fetchFeed(String apiKey);
    }

    private final FeedClient feedClient;

    public ExampleThreatSourceAdapter(FeedClient feedClient) {
        if (feedClient == null) {
            throw new IllegalArgumentException("feedClient 不得為 null");
        }
        this.feedClient = feedClient;
    }

    @Override
    public SourceType sourceType() {
        return SourceType.MOCK_OPENPHISH;
    }

    @Override
    public SourceMetadata metadata() {
        return new SourceMetadata(
                "Example Feed",
                "第三方 adapter 範例來源:TAB 分隔的 URL / Domain / IPv4 清單",
                "https://feed.example.invalid",
                Set.of(IocType.URL, IocType.DOMAIN, IocType.IPV4),
                Tlp.CLEAR,
                RedistributionPolicy.ATTRIBUTION_REQUIRED,
                Duration.ofHours(6),
                true);
    }

    @Override
    public FetchResult fetch(FetchContext context) {
        if (context.maxRecords() <= 0) {
            throw new IllegalArgumentException("maxRecords 必須為正數:" + context.maxRecords());
        }
        String apiKey = context.config().get(API_KEY);
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("缺少憑證設定:" + API_KEY);
        }
        List<RawThreatRecord> all = parse(feedClient.fetchFeed(apiKey));
        List<RawThreatRecord> visible = context.since() == null
                ? all
                : all.stream()
                        .filter(record -> record.observedAt().isAfter(context.since()))
                        .toList();
        return page(visible, context);
    }

    /** cursor 為資料集內的 offset(十進位字串);hasMore 為 true 時呼叫端以 nextCursor 續抓。 */
    private static FetchResult page(List<RawThreatRecord> visible, FetchContext context) {
        int offset = context.cursor() == null ? 0 : Integer.parseInt(context.cursor());
        if (offset < 0 || offset > visible.size()) {
            throw new IllegalArgumentException("cursor 超出範圍:" + context.cursor());
        }
        int limit = Math.min(PAGE_SIZE, context.maxRecords());
        int end = Math.min(visible.size(), Math.addExact(offset, limit));
        boolean hasMore = end < visible.size();
        return new FetchResult(
                List.copyOf(visible.subList(offset, end)), hasMore ? String.valueOf(end) : null, hasMore);
    }

    /** 空行與 {@code #} 註解行略過;欄位數不符的行視為來源格式錯誤,整批放棄而不是靜默吞掉。 */
    private static List<RawThreatRecord> parse(String feed) {
        List<RawThreatRecord> records = new ArrayList<>();
        for (String line : feed.split("\n", -1)) {
            // 只去掉 CRLF 的 \r——不能用 strip(),它會連末欄的分隔 TAB 一起吃掉,使空的 tags 欄消失
            String row = line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
            if (row.isBlank() || row.stripLeading().startsWith("#")) {
                continue;
            }
            String[] fields = row.split("\t", -1);
            if (fields.length != FIELD_COUNT) {
                throw new IllegalStateException("ExampleFeed 格式錯誤,應有 " + FIELD_COUNT + " 個欄位:" + row);
            }
            records.add(toRecord(fields));
        }
        return List.copyOf(records);
    }

    private static RawThreatRecord toRecord(String[] fields) {
        return new RawThreatRecord(
                fields[0],
                IocType.valueOf(fields[1]),
                null,
                Instant.parse(fields[2]),
                fields[3].isEmpty() ? null : Integer.valueOf(fields[3]),
                fields[4].isEmpty() ? null : Severity.valueOf(fields[4]),
                null,
                tags(fields[5]),
                Map.of());
    }

    private static Set<String> tags(String field) {
        if (field.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(Arrays.asList(field.split(",")));
    }
}
