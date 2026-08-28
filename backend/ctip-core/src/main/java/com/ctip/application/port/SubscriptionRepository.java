package com.ctip.application.port;

import com.ctip.domain.plan.Subscription;
import com.ctip.domain.tenant.TenantId;
import java.util.Optional;

/**
 * 訂閱持久化 port(docs/spec/04-data-dictionary.md 表 18)。
 * B1(一 tenant 一份 ACTIVE)由部分唯一索引 {@code ux_subscriptions_active} 強制。
 */
public interface SubscriptionRepository {

    Optional<Subscription> findActiveByTenant(TenantId tenantId);

    Subscription save(Subscription subscription);
}
