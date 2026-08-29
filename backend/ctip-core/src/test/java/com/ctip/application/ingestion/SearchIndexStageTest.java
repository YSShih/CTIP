package com.ctip.application.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctip.domain.indicator.Indicator;
import com.ctip.domain.source.Reputation;
import com.ctip.domain.tenant.TenantId;
import com.ctip.sdk.RawThreatRecord;
import com.ctip.sdk.RedistributionPolicy;
import com.ctip.sdk.Tlp;
import com.ctip.testing.IndicatorTestBuilder;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Stage 11 SearchIndex(docs/spec/08-ingestion-sdk.md §8.2)。
 * 它只標記待索引的 indicator——寫出在交易提交後,由 {@code SearchIndexWriter} 負責。
 */
@Tag("unit")
class SearchIndexStageTest {

    private final SearchIndexStage stage = new SearchIndexStage();

    @Test
    void marksThePersistedIndicatorForIndexing() {
        Indicator indicator = IndicatorTestBuilder.activeIndicator(
                TenantId.PUBLIC, Tlp.CLEAR, RedistributionPolicy.PUBLIC_REDISTRIBUTABLE);
        IngestionContext context = context();
        context.indicator(indicator);

        assertThat(stage.execute(context).searchIndexTarget()).isEqualTo(indicator.id());
        assertThat(stage.name()).isEqualTo("SearchIndex");
    }

    /** 被前面的 stage 拒絕的記錄沒有 indicator;標記必須留空,否則寫出端會查一個不存在的 id。 */
    @Test
    void leavesTheTargetEmptyWhenThereIsNoIndicator() {
        assertThat(stage.execute(context()).searchIndexTarget()).isNull();
    }

    private static IngestionContext context() {
        RawThreatRecord raw = new RawThreatRecord(
                "mal.ctip-sample.net", null, null, IndicatorTestBuilder.T0, null, null, null, Set.of(), Map.of());
        SourceContext source = new SourceContext(
                IndicatorTestBuilder.SOURCE_A,
                TenantId.PUBLIC,
                Tlp.CLEAR,
                RedistributionPolicy.PUBLIC_REDISTRIBUTABLE,
                new Reputation(70),
                false);
        return new IngestionContext(raw, source, new BatchState(UUID.randomUUID(), null));
    }
}
