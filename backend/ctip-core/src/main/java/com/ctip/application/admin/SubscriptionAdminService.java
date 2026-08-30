package com.ctip.application.admin;

import com.ctip.application.port.ClockPort;
import com.ctip.application.port.EventPublisherPort;
import com.ctip.application.port.IdGeneratorPort;
import com.ctip.application.port.PlanRepository;
import com.ctip.application.port.SubscriptionRepository;
import com.ctip.domain.plan.BillingPeriod;
import com.ctip.domain.plan.Plan;
import com.ctip.domain.plan.PlanCode;
import com.ctip.domain.plan.Subscription;
import com.ctip.domain.plan.SubscriptionId;
import com.ctip.domain.plan.SubscriptionProvider;
import com.ctip.domain.tenant.TenantId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 由平台管理者指派租戶方案({@code PATCH /api/v1/admin/tenants/{id}/subscription})。
 *
 * <p>這支端點是 <strong>Phase 21 補上的</strong>(ADR 0031):{@code Subscription.changePlan}／
 * {@code cancel} 自 Phase 14 起就存在、{@code SUBSCRIPTION_CHANGED} 是 §13.5 強制的 26 種稽核行為之一,
 * 但 09 §9.1 沒有任何端點會呼叫它們——那條稽核行為與那兩個聚合方法都永遠不可達(執行規則 16)。
 * 04 表 18 已寫明 M2 的訂閱「由 SYSTEM_ADMIN 手動指派(provider = MANUAL)」,此處即是那條路徑。
 */
@Service
public class SubscriptionAdminService {

    private final SubscriptionRepository subscriptions;
    private final PlanRepository plans;
    private final IdGeneratorPort ids;
    private final ClockPort clock;
    private final EventPublisherPort events;

    public SubscriptionAdminService(
            SubscriptionRepository subscriptions,
            PlanRepository plans,
            IdGeneratorPort ids,
            ClockPort clock,
            EventPublisherPort events) {
        this.subscriptions = subscriptions;
        this.plans = plans;
        this.ids = ids;
        this.clock = clock;
        this.events = events;
    }

    /** 指派方案:沒有 ACTIVE 訂閱就建立一份,有就換方案(B3:已取消者需新建,由聚合強制)。 */
    @Transactional
    public Subscription assignPlan(TenantId tenantId, PlanCode planCode) {
        requireNotPublic(tenantId);
        Plan plan = plans.findByCode(planCode)
                .orElseThrow(() -> new AdminResourceNotFoundException("No such plan: " + planCode));
        BillingPeriod period = BillingPeriod.openEnded(clock.now());
        Subscription subscription = subscriptions
                .findActiveByTenant(tenantId)
                .map(existing -> {
                    existing.changePlan(plan, period);
                    return existing;
                })
                .orElseGet(() -> Subscription.subscribe(
                        new SubscriptionId(ids.nextId()), tenantId, plan, SubscriptionProvider.MANUAL, period));
        return publish(subscriptions.save(subscription), subscription);
    }

    @Transactional
    public Subscription cancel(TenantId tenantId) {
        requireNotPublic(tenantId);
        Subscription subscription = subscriptions
                .findActiveByTenant(tenantId)
                .orElseThrow(() -> new AdminResourceNotFoundException("Tenant has no active subscription"));
        subscription.cancel(clock.now());
        return publish(subscriptions.save(subscription), subscription);
    }

    /** 不變量 T3:public tenant 不得有訂閱。聚合也擋,這裡先擋是為了回 409 而不是 400。 */
    private static void requireNotPublic(TenantId tenantId) {
        if (tenantId.isPublic()) {
            throw new AdminConflictException("The public tenant cannot hold a subscription");
        }
    }

    private Subscription publish(Subscription saved, Subscription mutated) {
        mutated.pullEvents().forEach(events::publish);
        return saved;
    }
}
