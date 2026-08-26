package com.ctip.application.stix;

import static com.ctip.testing.IndicatorTestBuilder.DEMO_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * bundle 匯出(§7.8.5):只含被引用的 marking、再散布過濾(§7.9 規則 3)、
 * 物件數上限(marking + indicator 合計)超過即丟出例外。
 */
@Tag("unit")
class StixExportServiceTest {

    private static final UUID BUNDLE_UUID = UUID.fromString("3c9d8e7f-6b2a-4d5e-a1b2-c3d4e5f60718");

    @Test
    void bundleContainsOnlyReferencedMarkingsAndVisibleIndicators() {
        Indicator clear = indicator(Tlp.CLEAR, RedistributionPolicy.PUBLIC_REDISTRIBUTABLE);
        Indicator green = indicator(Tlp.GREEN, RedistributionPolicy.ATTRIBUTION_REQUIRED);
        StixExportService service = service(List.of(clear, green), 1000);

        StixBundle bundle = service.exportBundle(Visibility.authenticated(DEMO_TENANT));

        assertThat(bundle.bundleId()).isEqualTo("bundle--" + BUNDLE_UUID);
        assertThat(bundle.markings())
                .extracting(m -> m.get("name"))
                .containsExactlyInAnyOrder("TLP:CLEAR", "TLP:GREEN"); // 不塞全部五個
        assertThat(bundle.indicatorContents()).hasSize(2).allMatch(c -> c.startsWith("{\"id\":\"indicator--"));
        assertThat(bundle.objectCount()).isEqualTo(4);
    }

    @Test
    void allInternalOnlyIndicatorIsExcludedForNonOwner() {
        Indicator internalOnly = indicator(Tlp.CLEAR, RedistributionPolicy.INTERNAL_ONLY);
        StixExportService service = service(List.of(internalOnly), 1000);

        // viewer(DEMO_TENANT)≠ owner(public tenant):I14 → 不得出現(§7.9 規則 3)
        StixBundle bundle = service.exportBundle(Visibility.authenticated(DEMO_TENANT));

        assertThat(bundle.indicatorContents()).isEmpty();
        assertThat(bundle.markings()).isEmpty();
    }

    @Test
    void exceedingMaxObjectsThrowsPlanLimit() {
        List<Indicator> three = List.of(
                indicator(Tlp.CLEAR, RedistributionPolicy.PUBLIC_REDISTRIBUTABLE),
                indicator(Tlp.CLEAR, RedistributionPolicy.PUBLIC_REDISTRIBUTABLE),
                indicator(Tlp.CLEAR, RedistributionPolicy.PUBLIC_REDISTRIBUTABLE));
        // 3 indicator + 1 marking = 4 > 3
        StixExportService service = service(three, 3);

        assertThatThrownBy(() -> service.exportBundle(Visibility.authenticated(DEMO_TENANT)))
                .isInstanceOf(StixExportLimitExceededException.class);
    }

    private static StixExportService service(List<Indicator> visible, int maxObjects) {
        return new StixExportService(
                repositoryOf(visible),
                new InMemoryStixObjects(visible),
                new RedistributionFilter(),
                () -> BUNDLE_UUID,
                new StixExportSettings(maxObjects));
    }

    /** 兩頁分頁(每頁 2 筆)驗證 collectExportable 的迴圈;findVisible 之外的方法不會被呼叫。 */
    private static IndicatorRepository repositoryOf(List<Indicator> visible) {
        return new IndicatorRepository() {
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
                return Optional.empty();
            }

            @Override
            public CursorPage<Indicator> findVisible(
                    Visibility visibility, IndicatorFilter filter, Cursor after, int limit) {
                return CursorPage.lastPage(visible);
            }

            @Override
            public Optional<Indicator> findVisibleByIdentity(
                    IocType type, String normalizedValue, Visibility visibility) {
                return Optional.empty();
            }

            @Override
            public List<Indicator> findVisibleOffset(
                    Visibility visibility, IndicatorFilter filter, int offset, int limit) {
                return List.of();
            }

            @Override
            public List<Indicator> findExpirable(Instant now, int limit) {
                return List.of();
            }

            @Override
            public Indicator save(Indicator indicator) {
                return indicator;
            }
        };
    }

    /** content = 最小 JSON(id 欄位),鍵為 stixId。 */
    private static final class InMemoryStixObjects implements StixObjectPort {
        private final Map<String, String> contents;

        private InMemoryStixObjects(List<Indicator> indicators) {
            this.contents = indicators.stream()
                    .collect(Collectors.toMap(
                            i -> "indicator--" + i.id().value(),
                            i -> "{\"id\":\"indicator--" + i.id().value() + "\"}"));
        }

        @Override
        public Optional<Instant> findCreated(String stixId) {
            return Optional.empty();
        }

        @Override
        public void upsert(StixProjection projection) {
            throw new UnsupportedOperationException("匯出不寫入");
        }

        @Override
        public Optional<String> findContent(String stixId) {
            return Optional.ofNullable(contents.get(stixId));
        }

        @Override
        public Map<String, String> findContents(Collection<String> stixIds) {
            return stixIds.stream().filter(contents::containsKey).collect(Collectors.toMap(id -> id, contents::get));
        }
    }

    private static final java.util.concurrent.atomic.AtomicLong SEQUENCE =
            new java.util.concurrent.atomic.AtomicLong(1);

    /** 每筆唯一 id(builder 的 activeIndicator 用固定 id,多筆會撞 stixId)。 */
    private static Indicator indicator(Tlp tlp, RedistributionPolicy policy) {
        long seq = SEQUENCE.getAndIncrement();
        var report = com.ctip.testing.IndicatorTestBuilder.report(com.ctip.testing.IndicatorTestBuilder.SOURCE_A)
                .tlp(tlp)
                .policy(policy)
                .build();
        var cmd = new com.ctip.domain.indicator.NewIndicatorCommand(
                new IndicatorId(new UUID(0, seq)),
                TenantId.PUBLIC,
                com.ctip.testing.IndicatorTestBuilder.domainValue("mal-" + seq + ".ctip-sample.net"),
                report,
                new com.ctip.domain.source.Reputation(70));
        return Indicator.create(cmd, new com.ctip.domain.fingerprint.Sha256FingerprintStrategy());
    }
}
