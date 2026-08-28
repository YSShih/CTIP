package com.ctip.infrastructure.persistence;

import com.ctip.domain.plan.BillingPeriod;
import com.ctip.domain.plan.PlanCode;
import com.ctip.domain.plan.PlanId;
import com.ctip.domain.plan.Subscription;
import com.ctip.domain.plan.SubscriptionId;
import com.ctip.domain.plan.SubscriptionProvider;
import com.ctip.domain.plan.SubscriptionSnapshot;
import com.ctip.domain.plan.SubscriptionStatus;
import com.ctip.domain.tenant.TenantId;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

/** Subscription domain ↔ JPA entity;planCode 由 plans 表關聯載入後傳入。 */
@Mapper(componentModel = "spring")
interface SubscriptionMapper {

    default Subscription toDomain(SubscriptionEntity e, PlanCode planCode) {
        return Subscription.reconstitute(
                new SubscriptionSnapshot(
                        new SubscriptionId(e.id),
                        new TenantId(e.tenantId),
                        new PlanId(e.planId),
                        SubscriptionStatus.valueOf(e.status),
                        SubscriptionProvider.valueOf(e.provider),
                        e.externalSubscriptionId,
                        new BillingPeriod(e.currentPeriodStart, e.currentPeriodEnd),
                        e.cancelledAt),
                planCode);
    }

    default void updateEntity(Subscription subscription, @MappingTarget SubscriptionEntity e) {
        SubscriptionSnapshot s = subscription.snapshot();
        e.id = s.id().value();
        e.tenantId = s.tenantId().value();
        e.planId = s.planId().value();
        e.status = s.status().name();
        e.provider = s.provider().name();
        e.externalSubscriptionId = s.externalSubscriptionId();
        e.currentPeriodStart = s.period().start();
        e.currentPeriodEnd = s.period().end();
        e.cancelledAt = s.cancelledAt();
    }
}
