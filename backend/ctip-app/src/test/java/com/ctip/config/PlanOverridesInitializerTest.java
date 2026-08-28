package com.ctip.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ctip.application.port.PlanRepository;
import com.ctip.domain.bloom.BloomCompression;
import com.ctip.domain.plan.Plan;
import com.ctip.domain.plan.PlanCode;
import com.ctip.domain.plan.PlanId;
import com.ctip.domain.plan.QuotaLimit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * {@code CTIP_PLAN_OVERRIDES}(docs/spec/10-identity-plans.md §10.6;ADR 0019)。
 *
 * <p>重點在<strong>打錯字要立刻失敗</strong>:被靜默忽略的覆寫,等於營運方以為配額調過了、
 * 實際上沒有——那比起不了機器更危險。
 */
@Tag("unit")
class PlanOverridesInitializerTest {

    private final RecordingPlans plans = new RecordingPlans();

    private void run(String overrides) {
        new PlanOverridesInitializer(plans, properties(overrides)).run(null);
    }

    @Test
    void emptyOverridesLeaveThePlansUntouched() {
        run("");
        run("   ");

        assertThat(plans.saved).isEmpty();
    }

    @Test
    void appliesOnlyTheNamedFields() {
        run("{\"PREMIUM\":{\"maxApiKeys\":20}}");

        assertThat(plans.saved).singleElement().satisfies(plan -> {
            assertThat(plan.maxApiKeys().orElse(0)).isEqualTo(20);
            // 其餘欄位必須原封不動
            assertThat(plan.maxManualSubmissionsPerDay().orElse(0)).isEqualTo(1000);
            assertThat(plan.maxPageSize()).isEqualTo(500);
        });
    }

    /** JSON 的 null 就是 plans 表的 NULL:無限制。 */
    @Test
    void jsonNullMeansUnlimited() {
        run("{\"PREMIUM\":{\"requestsPerDay\":null}}");

        assertThat(plans.saved)
                .singleElement()
                .satisfies(
                        plan -> assertThat(plan.requestsPerDay().isUnlimited()).isTrue());
    }

    @Test
    void unchangedValuesAreNotWrittenBack() {
        run("{\"PREMIUM\":{\"maxApiKeys\":10}}");

        assertThat(plans.saved).isEmpty();
    }

    @Test
    void unknownPlanCodeFailsFast() {
        assertThatThrownBy(() -> run("{\"GOLD\":{\"maxApiKeys\":20}}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GOLD");
    }

    @Test
    void unknownFieldNameFailsFast() {
        assertThatThrownBy(() -> run("{\"PREMIUM\":{\"maxApiKey\":20}}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("maxApiKey");
    }

    @Test
    void malformedJsonFailsFast() {
        assertThatThrownBy(() -> run("{\"PREMIUM\":"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CTIP_PLAN_OVERRIDES");
    }

    private static CtipProperties properties(String overrides) {
        return new CtipProperties(
                CtipProperties.Environment.MVP,
                new CtipProperties.Cors("http://localhost:5173"),
                new CtipProperties.Jwt("x".repeat(32), 900, 2592000, 90),
                new CtipProperties.Security(10, 15),
                new CtipProperties.RateLimit(true, CtipProperties.RateLimit.Backend.MEMORY),
                new CtipProperties.Plan(overrides),
                new CtipProperties.Ingestion(true, 500),
                new CtipProperties.Scheduler(true, "0 */5 * * * *", "0 0 3 * * *", "0 */15 * * * *"),
                new CtipProperties.Normalization(false),
                new CtipProperties.Api(50),
                new CtipProperties.DataQuality(List.of()),
                new CtipProperties.Bloom(
                        10_000_000,
                        0.001,
                        1_000_000,
                        "0 0 4 * * *",
                        "0 0 * * * *",
                        24,
                        "/var/lib/ctip/bloom",
                        BloomCompression.ZSTD),
                new CtipProperties.Retention(180, 30, 30, 30, 365, 30));
    }

    /** PREMIUM 一份(§10.6 的值);save 只記錄,不改變後續查詢結果。 */
    private static final class RecordingPlans implements PlanRepository {

        private final List<Plan> saved = new ArrayList<>();

        @Override
        public Optional<Plan> findByCode(PlanCode code) {
            return code == PlanCode.PREMIUM ? Optional.of(premium()) : Optional.empty();
        }

        @Override
        public Optional<Plan> findById(PlanId id) {
            return Optional.empty();
        }

        @Override
        public List<Plan> findAll() {
            return List.of(premium());
        }

        @Override
        public Plan save(Plan plan) {
            saved.add(plan);
            return plan;
        }

        private static Plan premium() {
            return new Plan(
                    new PlanId(new UUID(0, 2)),
                    PlanCode.PREMIUM,
                    "Premium",
                    2,
                    QuotaLimit.of(1200L),
                    QuotaLimit.of(500_000L),
                    500,
                    QuotaLimit.of(1000L),
                    300,
                    true,
                    QuotaLimit.of(1_000_000L),
                    true,
                    QuotaLimit.of(5L),
                    QuotaLimit.of(10L),
                    false,
                    QuotaLimit.of(50_000L),
                    QuotaLimit.of(1000L),
                    QuotaLimit.of(10_000L));
        }
    }
}
