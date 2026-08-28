package com.ctip.domain.event;

import com.ctip.domain.plan.PlanCode;
import com.ctip.domain.plan.SubscriptionId;
import com.ctip.domain.plan.SubscriptionStatus;
import com.ctip.domain.tenant.TenantId;

/**
 * Subscription 聚合發佈的 M2 事件(docs/spec/02-ddd-model.md §2.4)。
 * 消費者為 Audit(M3)與 Notification(M3);M2 只發佈,程序內無 listener。
 */
public interface SubscriptionEvents {

    /** 方案異動與取消共用:取消時 previousPlan 與 newPlan 相同,由 status 表達。 */
    record SubscriptionChanged(
            TenantId tenantId,
            SubscriptionId subscriptionId,
            PlanCode previousPlan,
            PlanCode newPlan,
            SubscriptionStatus status)
            implements DomainEvent {}
}
