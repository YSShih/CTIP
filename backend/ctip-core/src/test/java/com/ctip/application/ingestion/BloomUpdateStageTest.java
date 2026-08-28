package com.ctip.application.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctip.application.bloom.BloomChangeTracker;
import com.ctip.domain.bloom.BloomScope;
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
 * Stage 10 BloomUpdate:標記受影響的 scope,並且<strong>不得</strong>影響攝取結果。
 * 兩個 scope 的述詞不同——tenant 沒有再散布條件(ADR 0019)。
 */
@Tag("unit")
class BloomUpdateStageTest {

    private final BloomChangeTracker changes = new BloomChangeTracker();
    private final BloomUpdateStage stage = new BloomUpdateStage(changes);

    @Test
    void aPublicClearIndicatorMarksThePublicScope() {
        changes.markGenerated(BloomScope.PUBLIC, TenantId.PUBLIC);
        Indicator indicator = IndicatorTestBuilder.activeIndicator(
                TenantId.PUBLIC, Tlp.CLEAR, RedistributionPolicy.PUBLIC_REDISTRIBUTABLE);

        stage.execute(contextFor(indicator));

        assertThat(changes.hasChanges(BloomScope.PUBLIC, TenantId.PUBLIC)).isTrue();
    }

    @Test
    void anInternalOnlyTenantIndicatorStillMarksTheTenantScope() {
        TenantId owner = IndicatorTestBuilder.DEMO_TENANT;
        changes.markGenerated(BloomScope.TENANT, owner);
        changes.markGenerated(BloomScope.PUBLIC, TenantId.PUBLIC);
        Indicator indicator =
                IndicatorTestBuilder.activeIndicator(owner, Tlp.AMBER, RedistributionPolicy.INTERNAL_ONLY);

        stage.execute(contextFor(indicator));

        assertThat(changes.hasChanges(BloomScope.TENANT, owner)).isTrue();
        assertThat(changes.hasChanges(BloomScope.PUBLIC, TenantId.PUBLIC)).isFalse();
    }

    @Test
    void anIndicatorThatBelongsToNoBloomMarksNothing() {
        TenantId owner = IndicatorTestBuilder.DEMO_TENANT;
        changes.markGenerated(BloomScope.TENANT, owner);
        changes.markGenerated(BloomScope.PUBLIC, TenantId.PUBLIC);
        Indicator indicator =
                IndicatorTestBuilder.activeIndicator(owner, Tlp.GREEN, RedistributionPolicy.INTERNAL_ONLY);

        IngestionContext result = stage.execute(contextFor(indicator));

        assertThat(changes.hasChanges(BloomScope.TENANT, owner)).isFalse();
        assertThat(changes.hasChanges(BloomScope.PUBLIC, TenantId.PUBLIC)).isFalse();
        assertThat(result.rejected()).isFalse();
    }

    @Test
    void theStageIsNamedAfterItsPipelinePosition() {
        assertThat(stage.name()).isEqualTo("BloomUpdate");
    }

    private static IngestionContext contextFor(Indicator indicator) {
        RawThreatRecord raw = new RawThreatRecord(
                "mal-example.ctip-sample.net",
                null,
                null,
                IndicatorTestBuilder.T0,
                null,
                null,
                null,
                Set.of(),
                Map.of());
        SourceContext source = new SourceContext(
                IndicatorTestBuilder.SOURCE_A,
                indicator.ownerTenantId(),
                indicator.tlp(),
                RedistributionPolicy.INTERNAL_ONLY,
                new Reputation(70),
                false);
        IngestionContext context = new IngestionContext(raw, source, new BatchState(UUID.randomUUID(), null));
        context.indicator(indicator);
        return context;
    }
}
