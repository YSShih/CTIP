package com.ctip.domain.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ctip.domain.event.SubscriptionEvents.SubscriptionChanged;
import com.ctip.domain.tenant.TenantId;
import com.ctip.testing.PlanFixtures;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Subscription 的不變量 B2–B5 與 {@code effectivePlanCode}(docs/spec/02-ddd-model.md)。 */
@Tag("unit")
class SubscriptionTest {

    private static final SubscriptionId ID = new SubscriptionId(new UUID(0, 1));
    private static final TenantId TENANT = new TenantId(new UUID(0, 7));
    private static final Instant NOW = Instant.parse("2026-08-28T00:00:00Z");

    private static Subscription premium() {
        return Subscription.subscribe(
                ID,
                TENANT,
                PlanFixtures.of(PlanCode.PREMIUM),
                SubscriptionProvider.MANUAL,
                BillingPeriod.openEnded(NOW));
    }

    @Test
    void b2PeriodEndMustBeAfterStart() {
        assertThatThrownBy(() -> new BillingPeriod(NOW, NOW.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new BillingPeriod(NOW, null).hasEndedBy(NOW.plusSeconds(999)))
                .isFalse();
    }

    @Test
    void b3CancelledSubscriptionCannotChangePlan() {
        Subscription subscription = premium();
        subscription.cancel(NOW);

        assertThatThrownBy(() ->
                        subscription.changePlan(PlanFixtures.of(PlanCode.ENTERPRISE), BillingPeriod.openEnded(NOW)))
                .isInstanceOf(IllegalStateException.class);
    }

    /** B4:非 ACTIVE 或計費區間已過的訂閱一律降回 FREE(取安全側,§0.4 優先序)。 */
    @Test
    void b4EffectivePlanFallsBackToFree() {
        Subscription cancelled = premium();
        cancelled.cancel(NOW);
        assertThat(cancelled.effectivePlanCode(NOW)).isEqualTo(PlanCode.FREE);

        Subscription expired = Subscription.subscribe(
                ID,
                TENANT,
                PlanFixtures.of(PlanCode.PREMIUM),
                SubscriptionProvider.MANUAL,
                new BillingPeriod(NOW, NOW.plusSeconds(60)));
        assertThat(expired.effectivePlanCode(NOW.plusSeconds(61))).isEqualTo(PlanCode.FREE);
        assertThat(expired.effectivePlanCode(NOW)).isEqualTo(PlanCode.PREMIUM);
    }

    @Test
    void b5NoneProviderMustNotCarryAnExternalId() {
        assertThatThrownBy(() -> Subscription.reconstitute(
                        new SubscriptionSnapshot(
                                ID,
                                TENANT,
                                PlanFixtures.idOf(PlanCode.FREE),
                                SubscriptionStatus.ACTIVE,
                                SubscriptionProvider.NONE,
                                "sub_external_1",
                                BillingPeriod.openEnded(NOW),
                                null),
                        PlanCode.FREE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** T3:public tenant 不得有訂閱。 */
    @Test
    void publicTenantCannotSubscribe() {
        assertThatThrownBy(() -> Subscription.subscribe(
                        ID,
                        TenantId.PUBLIC,
                        PlanFixtures.of(PlanCode.PREMIUM),
                        SubscriptionProvider.MANUAL,
                        BillingPeriod.openEnded(NOW)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void planChangeAndCancellationEmitTheSameEvent() {
        Subscription subscription = premium();
        subscription.pullEvents();

        subscription.changePlan(PlanFixtures.of(PlanCode.ENTERPRISE), BillingPeriod.openEnded(NOW));
        assertThat(subscription.pullEvents())
                .singleElement()
                .isInstanceOfSatisfying(SubscriptionChanged.class, event -> {
                    assertThat(event.previousPlan()).isEqualTo(PlanCode.PREMIUM);
                    assertThat(event.newPlan()).isEqualTo(PlanCode.ENTERPRISE);
                });

        subscription.cancel(NOW);
        assertThat(subscription.pullEvents())
                .singleElement()
                .isInstanceOfSatisfying(
                        SubscriptionChanged.class,
                        event -> assertThat(event.status()).isEqualTo(SubscriptionStatus.CANCELLED));
    }
}
