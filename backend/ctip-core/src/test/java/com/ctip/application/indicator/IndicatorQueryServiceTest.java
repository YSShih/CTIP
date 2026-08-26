package com.ctip.application.indicator;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctip.application.port.SearchPort;
import com.ctip.domain.indicator.Indicator;
import com.ctip.domain.indicator.normalization.IocNormalizers;
import com.ctip.domain.shared.CursorPage;
import com.ctip.domain.shared.Visibility;
import com.ctip.domain.tenant.TenantId;
import com.ctip.sdk.RedistributionPolicy;
import com.ctip.sdk.Tlp;
import com.ctip.testing.InMemoryIndicatorRepository;
import com.ctip.testing.IndicatorTestBuilder;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * lookup 的推斷 + 正規化 + 識別鍵查詢(docs/spec/09-api.md §9.1):
 * 無法正規化/推斷/不可見一律未命中,不報錯、不洩漏存在性。
 */
@Tag("unit")
class IndicatorQueryServiceTest {

    private final InMemoryIndicatorRepository repository = new InMemoryIndicatorRepository();
    private final SearchPort search = (term, filter, visibility, after, limit) -> CursorPage.lastPage(List.of());
    private final IndicatorQueryService service =
            new IndicatorQueryService(repository, search, new IocNormalizers(false));

    @Test
    void lookupNormalizesBeforeIdentityMatch() {
        Indicator stored = IndicatorTestBuilder.activeIndicator(
                TenantId.PUBLIC, Tlp.CLEAR, RedistributionPolicy.PUBLIC_REDISTRIBUTABLE);
        repository.save(stored); // normalized = mal-example.ctip-sample.net

        List<LookupResult> results = service.lookup(
                List.of("MAL-Example.CTIP-Sample.NET.", "unknown.ctip-sample.net"), Visibility.anonymous());

        assertThat(results.get(0).found()).isTrue(); // 大小寫 + 尾端點經正規化後命中
        assertThat(results.get(0).indicator().id()).isEqualTo(stored.id());
        assertThat(results.get(1).found()).isFalse();
    }

    @Test
    void unparsableOrUnknownTypeValuesAreMissesNotErrors() {
        List<LookupResult> results =
                service.lookup(List.of("!!!not-an-ioc!!!", "999.999.999.999", ""), Visibility.anonymous());
        assertThat(results).allSatisfy(r -> assertThat(r.found()).isFalse());
        assertThat(results).extracting(LookupResult::value).containsExactly("!!!not-an-ioc!!!", "999.999.999.999", "");
    }

    @Test
    void invisibleIndicatorIsAMiss() {
        Indicator otherTenantsAmber = IndicatorTestBuilder.activeIndicator(
                IndicatorTestBuilder.DEMO_TENANT, Tlp.AMBER, RedistributionPolicy.PUBLIC_REDISTRIBUTABLE);
        repository.save(otherTenantsAmber);

        assertThat(service.lookup(List.of("mal-example.ctip-sample.net"), Visibility.anonymous()))
                .singleElement()
                .satisfies(r -> assertThat(r.found()).isFalse());
    }
}
