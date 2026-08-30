package com.ctip.application.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ctip.domain.event.DomainEvent;
import com.ctip.domain.event.SubscriptionEvents;
import com.ctip.domain.plan.PlanCode;
import com.ctip.domain.plan.Subscription;
import com.ctip.domain.plan.SubscriptionStatus;
import com.ctip.domain.tenant.TenantId;
import com.ctip.testing.FixedClockPort;
import com.ctip.testing.InMemoryPlanRepository;
import com.ctip.testing.InMemorySubscriptionRepository;
import com.ctip.testing.RecordingEventPublisher;
import com.ctip.testing.SequentialIdGenerator;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * 由平台管理者指派方案(ADR 0031)。這是 {@code SUBSCRIPTION_CHANGED} 稽核行為與
 * {@code Subscription.changePlan}／{@code cancel} 唯一的呼叫端——沒有它,兩者都永不可達。
 */
@Tag("unit")
class SubscriptionAdminServiceTest {

    private static final TenantId TENANT = new TenantId(UUID.fromString("11111111-1111-1111-1111-111111111111"));

    private InMemorySubscriptionRepository subscriptions;
    private RecordingEventPublisher events;
    private SubscriptionAdminService service;

    @BeforeEach
    void setUp() {
        subscriptions = new InMemorySubscriptionRepository();
        events = new RecordingEventPublisher();
        service = new SubscriptionAdminService(
                subscriptions,
                new InMemoryPlanRepository(),
                new SequentialIdGenerator(),
                FixedClockPort.at(FixedClockPort.DEFAULT_NOW),
                events);
    }

    @Test
    void assigningAPlanToATenantWithoutOneCreatesAnActiveSubscription() {
        Subscription created = service.assignPlan(TENANT, PlanCode.PREMIUM);

        assertThat(created.planCode()).isEqualTo(PlanCode.PREMIUM);
        assertThat(created.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(changedEvents()).hasSize(1);
    }

    @Test
    void assigningAgainChangesThePlanOfTheExistingSubscription() {
        Subscription first = service.assignPlan(TENANT, PlanCode.PREMIUM);

        Subscription second = service.assignPlan(TENANT, PlanCode.ENTERPRISE);

        assertThat(second.snapshot().id()).isEqualTo(first.snapshot().id());
        assertThat(second.planCode()).isEqualTo(PlanCode.ENTERPRISE);
        assertThat(changedEvents()).hasSize(2);
    }

    @Test
    void cancellingMarksTheSubscriptionCancelledAndPublishesTheChange() {
        service.assignPlan(TENANT, PlanCode.PREMIUM);

        Subscription cancelled = service.cancel(TENANT);

        assertThat(cancelled.status()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(cancelled.snapshot().cancelledAt()).isNotNull();
        assertThat(changedEvents()).hasSize(2);
    }

    /** 不變量 B3:取消之後不得回到 ACTIVE——再指派會建立<strong>新的一份</strong>訂閱。 */
    @Test
    void assigningAfterCancellationCreatesANewSubscription() {
        Subscription original = service.assignPlan(TENANT, PlanCode.PREMIUM);
        service.cancel(TENANT);

        Subscription fresh = service.assignPlan(TENANT, PlanCode.PREMIUM);

        assertThat(fresh.snapshot().id()).isNotEqualTo(original.snapshot().id());
        assertThat(fresh.status()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void cancellingWithoutAnActiveSubscriptionIsNotFound() {
        assertThatThrownBy(() -> service.cancel(TENANT)).isInstanceOf(AdminResourceNotFoundException.class);
    }

    /** 不變量 T3:public tenant 不得有訂閱;此處先擋是為了回 409 而不是 400。 */
    @Test
    void thePublicTenantCannotHoldASubscription() {
        assertThatThrownBy(() -> service.assignPlan(TenantId.PUBLIC, PlanCode.PREMIUM))
                .isInstanceOf(AdminConflictException.class);
    }

    private java.util.List<DomainEvent> changedEvents() {
        return events.published().stream()
                .filter(event -> event instanceof SubscriptionEvents.SubscriptionChanged)
                .toList();
    }
}
