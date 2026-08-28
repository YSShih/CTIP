package com.ctip.infrastructure.persistence;

import com.ctip.application.port.SubscriptionRepository;
import com.ctip.domain.plan.PlanCode;
import com.ctip.domain.plan.Subscription;
import com.ctip.domain.plan.SubscriptionStatus;
import com.ctip.domain.tenant.TenantId;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * SubscriptionRepository port 的 JPA 實作。
 * <strong>不快取</strong>:方案定義可以快取(四列、極少變動),但「哪個租戶用哪個方案」
 * 必須立即生效——降級或取消若延遲一分鐘,那一分鐘內配額仍是舊方案的。
 */
@Repository
@Transactional
class SubscriptionRepositoryAdapter implements SubscriptionRepository {

    private final SubscriptionJpaRepository jpa;
    private final PlanJpaRepository plans;
    private final SubscriptionMapper mapper;

    SubscriptionRepositoryAdapter(SubscriptionJpaRepository jpa, PlanJpaRepository plans, SubscriptionMapper mapper) {
        this.jpa = jpa;
        this.plans = plans;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Subscription> findActiveByTenant(TenantId tenantId) {
        return jpa.findByTenantIdAndStatus(tenantId.value(), SubscriptionStatus.ACTIVE.name())
                .map(entity -> mapper.toDomain(entity, planCodeOf(entity)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TenantId> findActiveTenantIds() {
        return jpa.findTenantIdsByStatus(SubscriptionStatus.ACTIVE.name()).stream()
                .map(TenantId::new)
                .toList();
    }

    @Override
    public Subscription save(Subscription subscription) {
        SubscriptionEntity entity = jpa.findById(subscription.id().value()).orElseGet(SubscriptionEntity::new);
        mapper.updateEntity(subscription, entity);
        SubscriptionEntity saved = jpa.save(entity);
        return mapper.toDomain(saved, planCodeOf(saved));
    }

    private PlanCode planCodeOf(SubscriptionEntity entity) {
        return plans.findById(entity.planId)
                .map(plan -> PlanCode.valueOf(plan.code))
                .orElseThrow(() -> new IllegalStateException("訂閱指向不存在的方案:" + entity.planId));
    }
}
