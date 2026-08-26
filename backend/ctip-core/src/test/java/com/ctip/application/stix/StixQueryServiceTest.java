package com.ctip.application.stix;

import static com.ctip.testing.IndicatorTestBuilder.DEMO_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

import com.ctip.application.indicator.IndicatorFilter;
import com.ctip.application.indicator.RedistributionFilter;
import com.ctip.application.port.IndicatorRepository;
import com.ctip.application.port.StixObjectPort;
import com.ctip.domain.indicator.Indicator;
import com.ctip.domain.indicator.IndicatorId;
import com.ctip.domain.shared.Cursor;
import com.ctip.domain.shared.CursorPage;
import com.ctip.domain.shared.Visibility;
import com.ctip.domain.stix.StixProjection;
import com.ctip.domain.tenant.TenantId;
import com.ctip.sdk.IocType;
import com.ctip.sdk.RedistributionPolicy;
import com.ctip.sdk.Tlp;
import com.ctip.testing.IndicatorTestBuilder;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * GET /api/v1/stix/{stixId} 的查詢規則:marking 由常數供應、
 * indicator 經可見度 + 再散布過濾(§7.9 規則 3)、格式不合的 stixId 一律查無。
 */
@Tag("unit")
class StixQueryServiceTest {

    @Test
    void markingIsServedFromConstants() {
        StixQueryService service = service(null);
        assertThat(service.findMarking("marking-definition--94868c89-83c2-464b-929b-a1a8aa3c8487"))
                .hasValueSatisfying(m -> assertThat(m.get("name")).isEqualTo("TLP:CLEAR"));
        assertThat(service.findMarking("indicator--1f0d2c4e-93a5-4f6b-8c1d-2e3a4b5c6d7e"))
                .isEmpty();
    }

    @Test
    void visibleRedistributableIndicatorReturnsStoredContent() {
        Indicator indicator = IndicatorTestBuilder.activeIndicator(
                TenantId.PUBLIC, Tlp.CLEAR, RedistributionPolicy.PUBLIC_REDISTRIBUTABLE);
        StixQueryService service = service(indicator);

        assertThat(service.findIndicatorContent("indicator--" + indicator.id().value(), Visibility.anonymous()))
                .contains("{\"stored\":true}");
    }

    @Test
    void allInternalOnlyIndicatorIsHiddenFromNonOwner() {
        Indicator indicator =
                IndicatorTestBuilder.activeIndicator(TenantId.PUBLIC, Tlp.CLEAR, RedistributionPolicy.INTERNAL_ONLY);
        StixQueryService service = service(indicator);

        // 匿名雖綁 public tenant,仍屬公開輸出,不得豁免(§7.9 作用域修正的安全解讀;ADR 0006)
        assertThat(service.findIndicatorContent("indicator--" + indicator.id().value(), Visibility.anonymous()))
                .isEmpty();
        // 非擁有租戶 → I14 隱藏
        assertThat(service.findIndicatorContent(
                        "indicator--" + indicator.id().value(), Visibility.authenticated(DEMO_TENANT)))
                .isEmpty();
    }

    @Test
    void malformedStixIdsAreNotFound() {
        StixQueryService service = service(null);
        assertThat(service.findIndicatorContent("indicator--not-a-uuid", Visibility.anonymous()))
                .isEmpty();
        assertThat(service.findIndicatorContent("threat--1f0d2c4e-93a5-4f6b-8c1d-2e3a4b5c6d7e", Visibility.anonymous()))
                .isEmpty();
        assertThat(service.findIndicatorContent(null, Visibility.anonymous())).isEmpty();
    }

    /** findVisibleById 回傳固定 indicator(可見度過濾本身由 repository 整合測試覆蓋)。 */
    private static StixQueryService service(Indicator indicator) {
        return new StixQueryService(
                new FixedIndicatorRepository(indicator), new StoredContentPort(), new RedistributionFilter());
    }

    private record FixedIndicatorRepository(Indicator indicator) implements IndicatorRepository {
        @Override
        public Optional<Indicator> findById(IndicatorId id) {
            return Optional.empty();
        }

        @Override
        public Optional<Indicator> findByIdentity(IocType type, String normalizedValue, TenantId ownerTenantId) {
            return Optional.empty();
        }

        @Override
        public Optional<Indicator> findVisibleById(IndicatorId id, Visibility visibility) {
            return Optional.ofNullable(indicator).filter(i -> i.id().equals(id));
        }

        @Override
        public CursorPage<Indicator> findVisible(
                Visibility visibility, IndicatorFilter filter, Cursor after, int limit) {
            return CursorPage.lastPage(List.of());
        }

        @Override
        public Optional<Indicator> findVisibleByIdentity(IocType type, String normalizedValue, Visibility visibility) {
            return Optional.empty();
        }

        @Override
        public List<Indicator> findVisibleOffset(Visibility visibility, IndicatorFilter filter, int offset, int limit) {
            return List.of();
        }

        @Override
        public List<Indicator> findExpirable(Instant now, int limit) {
            return List.of();
        }

        @Override
        public Indicator save(Indicator saved) {
            return saved;
        }
    }

    private static final class StoredContentPort implements StixObjectPort {
        @Override
        public Optional<Instant> findCreated(String stixId) {
            return Optional.empty();
        }

        @Override
        public void upsert(StixProjection projection) {
            // 查詢不寫入
        }

        @Override
        public Optional<String> findContent(String stixId) {
            return Optional.of("{\"stored\":true}");
        }

        @Override
        public Map<String, String> findContents(Collection<String> stixIds) {
            return Map.of();
        }
    }
}
