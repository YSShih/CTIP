package com.ctip.application.indicator;

import static com.ctip.testing.IndicatorTestBuilder.DEMO_TENANT;
import static com.ctip.testing.IndicatorTestBuilder.SOURCE_A;
import static com.ctip.testing.IndicatorTestBuilder.SOURCE_B;
import static com.ctip.testing.IndicatorTestBuilder.SOURCE_C;
import static com.ctip.testing.IndicatorTestBuilder.report;
import static org.assertj.core.api.Assertions.assertThat;

import com.ctip.domain.indicator.Indicator;
import com.ctip.domain.indicator.IndicatorSource;
import com.ctip.domain.indicator.IndicatorSourceSnapshot;
import com.ctip.domain.source.Reputation;
import com.ctip.domain.source.SourceId;
import com.ctip.domain.tenant.TenantId;
import com.ctip.sdk.RedistributionPolicy;
import com.ctip.sdk.Tlp;
import com.ctip.testing.IndicatorTestBuilder;
import com.ctip.testing.TestMetrics;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * 再散布政策的輸出過濾單點(docs/spec/07-domain-intel.md §7.9):
 * 規則 3(全 INTERNAL_ONLY 隱藏)、規則 4(attribution)、規則 5(DERIVED_ONLY 遮罩明細)、
 * 作用域修正(擁有租戶看得到全貌)。
 */
@Tag("unit")
class RedistributionFilterTest {

    private final RedistributionFilter filter = new RedistributionFilter(TestMetrics.redistributionMetrics());

    @Test
    void ownerSeesAllSourceRecordsRegardlessOfPolicy() {
        Indicator indicator = mixedPolicyIndicator(DEMO_TENANT);
        assertThat(filter.visibleSourceRecords(indicator, DEMO_TENANT)).hasSize(3);
        assertThat(filter.redistributableTo(indicator, DEMO_TENANT)).isTrue();
    }

    @Test
    void anonymousPublicViewerIsPublicOutputNotOwner() {
        // 匿名綁 public tenant,但 public 無成員:對 public 資料仍屬公開輸出,不得豁免(ADR 0006)
        Indicator indicator = mixedPolicyIndicator(TenantId.PUBLIC);
        assertThat(filter.visibleSourceRecords(indicator, TenantId.PUBLIC)).hasSize(1);
    }

    @Test
    void crossTenantViewerOnlySeesDisclosablePolicies() {
        Indicator indicator = mixedPolicyIndicator(TenantId.PUBLIC);
        List<IndicatorSourceSnapshot> visible = filter.visibleSourceRecords(indicator, DEMO_TENANT);
        // DERIVED_ONLY(規則 5)與 INTERNAL_ONLY(規則 3)不得回傳來源明細
        assertThat(visible).hasSize(1);
        assertThat(visible.getFirst().redistributionPolicy()).isEqualTo(RedistributionPolicy.ATTRIBUTION_REQUIRED);
    }

    @Test
    void attributionListsOnlyAttributionRequiredSources() {
        Indicator indicator = mixedPolicyIndicator(TenantId.PUBLIC);
        List<SourceId> attribution = filter.attributionRequired(indicator, DEMO_TENANT).stream()
                .map(IndicatorSourceSnapshot::sourceId)
                .toList();
        assertThat(attribution).containsExactly(SOURCE_A);
    }

    @Test
    void allInternalOnlyIndicatorIsNotRedistributableToNonOwner() {
        Indicator indicator =
                IndicatorTestBuilder.activeIndicator(DEMO_TENANT, Tlp.AMBER, RedistributionPolicy.INTERNAL_ONLY);
        assertThat(filter.redistributableTo(indicator, DEMO_TENANT)).isTrue(); // 擁有租戶(非 public)豁免
        assertThat(filter.redistributableTo(indicator, TenantId.PUBLIC)).isFalse();
        assertThat(filter.visibleSourceRecords(indicator, TenantId.PUBLIC)).isEmpty();

        Indicator publicInternalOnly =
                IndicatorTestBuilder.activeIndicator(TenantId.PUBLIC, Tlp.CLEAR, RedistributionPolicy.INTERNAL_ONLY);
        assertThat(filter.redistributableTo(publicInternalOnly, TenantId.PUBLIC))
                .isFalse(); // 公開輸出無豁免
    }

    /** A=ATTRIBUTION_REQUIRED、B=DERIVED_ONLY、C=INTERNAL_ONLY 的三來源 indicator。 */
    private static Indicator mixedPolicyIndicator(TenantId owner) {
        Indicator indicator =
                IndicatorTestBuilder.activeIndicator(owner, Tlp.CLEAR, RedistributionPolicy.ATTRIBUTION_REQUIRED);
        indicator.mergeFrom(
                new IndicatorSource(report(SOURCE_B)
                        .policy(RedistributionPolicy.DERIVED_ONLY)
                        .build()),
                new Reputation(60));
        indicator.mergeFrom(
                new IndicatorSource(report(SOURCE_C)
                        .policy(RedistributionPolicy.INTERNAL_ONLY)
                        .build()),
                new Reputation(60));
        return indicator;
    }
}
