package com.ctip.application.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctip.domain.plan.BillingPeriod;
import com.ctip.domain.plan.PlanCode;
import com.ctip.domain.plan.Subscription;
import com.ctip.domain.plan.SubscriptionId;
import com.ctip.domain.plan.SubscriptionProvider;
import com.ctip.domain.tenant.Tenant;
import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.tenant.TenantSlug;
import com.ctip.domain.tenant.TenantType;
import com.ctip.testing.FixedClockPort;
import com.ctip.testing.InMemorySubscriptionRepository;
import com.ctip.testing.InMemoryTenantRepository;
import com.ctip.testing.PlanFixtures;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** 管理端的租戶總覽:沒有 ACTIVE 訂閱的租戶回 FREE(§10.6 的預設方案)。 */
@Tag("unit")
class TenantAdminServiceTest {

    @Test
    void tenantsAreListedWithTheirCurrentPlan() {
        InMemoryTenantRepository tenants = new InMemoryTenantRepository();
        InMemorySubscriptionRepository subscriptions = new InMemorySubscriptionRepository();
        TenantId paying = new TenantId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        tenants.save(Tenant.create(paying, new TenantSlug("paying"), "Paying", TenantType.ORGANIZATION));
        TenantId free = new TenantId(UUID.fromString("33333333-3333-3333-3333-333333333333"));
        tenants.save(Tenant.create(free, new TenantSlug("free-tenant"), "Free", TenantType.INDIVIDUAL));
        subscriptions.save(Subscription.subscribe(
                new SubscriptionId(UUID.randomUUID()),
                paying,
                PlanFixtures.of(PlanCode.PREMIUM),
                SubscriptionProvider.MANUAL,
                BillingPeriod.openEnded(FixedClockPort.DEFAULT_NOW)));

        List<TenantOverview> overviews = new TenantAdminService(tenants, subscriptions).list();

        assertThat(overviews)
                .extracting(TenantOverview::slug, TenantOverview::planCode)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("paying", PlanCode.PREMIUM),
                        org.assertj.core.groups.Tuple.tuple("free-tenant", PlanCode.FREE));
    }
}
