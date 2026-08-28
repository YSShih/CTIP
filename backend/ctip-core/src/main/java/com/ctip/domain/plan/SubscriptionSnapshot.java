package com.ctip.domain.plan;

import com.ctip.domain.tenant.TenantId;
import java.time.Instant;

/** Subscription 的持久化快照。 */
public record SubscriptionSnapshot(
        SubscriptionId id,
        TenantId tenantId,
        PlanId planId,
        SubscriptionStatus status,
        SubscriptionProvider provider,
        String externalSubscriptionId,
        BillingPeriod period,
        Instant cancelledAt) {}
