package com.ctip.application.admin;

import com.ctip.application.port.SubscriptionRepository;
import com.ctip.application.port.TenantRepository;
import com.ctip.domain.plan.PlanCode;
import com.ctip.domain.tenant.Tenant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 平台管理者的租戶總覽({@code GET /api/v1/admin/tenants};docs/spec/09-api.md §9.1「管理」)。 */
@Service
public class TenantAdminService {

    private final TenantRepository tenants;
    private final SubscriptionRepository subscriptions;

    public TenantAdminService(TenantRepository tenants, SubscriptionRepository subscriptions) {
        this.tenants = tenants;
        this.subscriptions = subscriptions;
    }

    /** 全部租戶(含 public tenant)與其目前的 ACTIVE 方案;無訂閱者為 {@code FREE}(§10.6)。 */
    @Transactional(readOnly = true)
    public List<TenantOverview> list() {
        return tenants.findAll().stream().map(this::overview).toList();
    }

    private TenantOverview overview(Tenant tenant) {
        PlanCode plan = subscriptions
                .findActiveByTenant(tenant.id())
                .map(com.ctip.domain.plan.Subscription::planCode)
                .orElse(PlanCode.FREE);
        return new TenantOverview(
                tenant.id(), tenant.slug().value(), tenant.name(), tenant.type(), tenant.status(), plan);
    }
}
