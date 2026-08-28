package com.ctip.support;

import com.ctip.application.port.ClockPort;
import com.ctip.application.port.IdGeneratorPort;
import com.ctip.application.port.PlanRepository;
import com.ctip.application.port.SubscriptionRepository;
import com.ctip.application.port.TenantRepository;
import com.ctip.domain.plan.BillingPeriod;
import com.ctip.domain.plan.Plan;
import com.ctip.domain.plan.PlanCode;
import com.ctip.domain.plan.Subscription;
import com.ctip.domain.plan.SubscriptionId;
import com.ctip.domain.plan.SubscriptionProvider;
import com.ctip.domain.tenant.Tenant;
import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.tenant.TenantSlug;
import com.ctip.domain.tenant.TenantType;

/**
 * Bloom 測試專用的租戶建立與方案指派。
 *
 * <p>tenant bloom 需要一個<strong>真實存在</strong>的租戶({@code bloom_versions.tenant_id} 有 FK),
 * 而種子的 demo 租戶被其他測試共用——這裡各測試類自建租戶,避免互相污染方案。
 * 兩個操作都冪等:整合測試共用同一個資料庫容器。
 */
public final class BloomTenants {

    private final TenantRepository tenants;
    private final SubscriptionRepository subscriptions;
    private final PlanRepository plans;
    private final IdGeneratorPort ids;
    private final ClockPort clock;

    public BloomTenants(
            TenantRepository tenants,
            SubscriptionRepository subscriptions,
            PlanRepository plans,
            IdGeneratorPort ids,
            ClockPort clock) {
        this.tenants = tenants;
        this.subscriptions = subscriptions;
        this.plans = plans;
        this.ids = ids;
        this.clock = clock;
    }

    public TenantId create(String slug) {
        TenantSlug tenantSlug = new TenantSlug(slug);
        return tenants.findBySlug(tenantSlug).map(Tenant::id).orElseGet(() -> {
            TenantId id = new TenantId(ids.nextId());
            tenants.save(Tenant.create(id, tenantSlug, slug, TenantType.ORGANIZATION));
            return id;
        });
    }

    /** 不變量 B1:一個租戶同時只有一份 ACTIVE 訂閱,已有就不再建立。 */
    public void assignPlan(TenantId tenantId, PlanCode code) {
        if (subscriptions.findActiveByTenant(tenantId).isPresent()) {
            return;
        }
        Plan plan = plans.findByCode(code).orElseThrow();
        subscriptions.save(Subscription.subscribe(
                new SubscriptionId(ids.nextId()),
                tenantId,
                plan,
                SubscriptionProvider.MANUAL,
                BillingPeriod.openEnded(clock.now())));
    }
}
