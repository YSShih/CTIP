package com.ctip.application.ingestion;

import com.ctip.domain.indicator.Indicator;

/**
 * Stage 11 SearchIndex(docs/spec/08-ingestion-sdk.md §8.2 的第 11 格,PersistStage 與
 * BloomUpdateStage 之後):標記本筆 indicator 需要重新索引。
 *
 * <p><strong>這裡不寫 Elasticsearch</strong>。理由與 {@code StixProjectionStage} 同源(ADR 0005):
 * stage 在批次交易內執行,若在此寫出,交易 rollback 後索引就會留下不存在的資料;
 * 而且外部系統的失敗會污染交易、使整批 rollback,違反 §13.7「索引失敗不得使 ingestion 失敗」。
 * 實際寫出由 {@code IngestionBatchExecutor} 在交易提交後交給 {@code SearchIndexWriter}。
 *
 * <p>只留 id 而不在此組文件,是因為文件需要 {@code updated_at} 等只有提交後才確定的欄位——
 * 提交後依 id 從 source of truth 讀回來,索引內容才會與資料庫一致。
 */
public final class SearchIndexStage implements IngestionStage {

    @Override
    public String name() {
        return "SearchIndex";
    }

    @Override
    public IngestionContext execute(IngestionContext context) {
        Indicator indicator = context.indicator();
        if (indicator != null) {
            context.searchIndexTarget(indicator.id());
        }
        return context;
    }
}
