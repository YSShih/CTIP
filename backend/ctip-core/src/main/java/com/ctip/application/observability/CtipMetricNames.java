package com.ctip.application.observability;

/**
 * CTIP 自訂指標的名稱(docs/spec/13-platform-ops.md §13.6 的必要指標清單)。
 *
 * <p>集中一處的理由與 {@code AuditEndpoints} 相同:規格的清單是強制的,而
 * {@code MetricsCompletenessTest} 逐項比對的就是這些常數——名稱只要在產生端與
 * 判準測試各寫一份字面值,改名時測試會跟著改而不會發現指標其實消失了。
 */
public final class CtipMetricNames {

    /** 攝取筆數,tag {@code result} ∈ accepted / rejected / merged。 */
    public static final String INGESTION_RECORDS = "ctip.ingestion.records";

    /** 每個 pipeline stage 的耗時,tag {@code stage} 為 {@code IngestionStage.name()}。 */
    public static final String INGESTION_STAGE_DURATION = "ctip.ingestion.stage.duration";

    /** 距上次成功同步的落後秒數,tag {@code source}。 */
    public static final String SOURCE_SYNC_LAG = "ctip.source.sync.lag";

    /** Bloom artifact 生成耗時,tag {@code scope} ∈ PUBLIC / TENANT。 */
    public static final String BLOOM_GENERATION_DURATION = "ctip.bloom.generation.duration";

    /** 被限流拒絕的請求數,tag {@code dimension} 為 §10.7 的五個維度。 */
    public static final String RATELIMIT_REJECTED = "ctip.ratelimit.rejected";

    /** 被再散布政策濾掉的來源明細筆數,tag {@code policy}。 */
    public static final String REDISTRIBUTION_FILTERED = "ctip.redistribution.filtered";

    private CtipMetricNames() {}
}
