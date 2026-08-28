package com.ctip.testing;

import com.ctip.application.port.SubscriptionRepository;
import com.ctip.domain.plan.Subscription;
import com.ctip.domain.plan.SubscriptionStatus;
import com.ctip.domain.tenant.TenantId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 測試用 subscriptions 表;B1(一租戶一份 ACTIVE)由查詢語意呈現,不模擬部分唯一索引。 */
public final class InMemorySubscriptionRepository implements SubscriptionRepository {

    private final List<Subscription> subscriptions = new ArrayList<>();

    @Override
    public Optional<Subscription> findActiveByTenant(TenantId tenantId) {
        return subscriptions.stream()
                .filter(s -> s.tenantId().equals(tenantId) && s.status() == SubscriptionStatus.ACTIVE)
                .findFirst();
    }

    @Override
    public Subscription save(Subscription subscription) {
        subscriptions.removeIf(existing -> existing.id().equals(subscription.id()));
        subscriptions.add(subscription);
        return subscription;
    }
}
