package com.ctip.domain.event;

import com.ctip.domain.tenant.TenantId;

/** Tenant 聚合發佈的 M2 事件(docs/spec/02-ddd-model.md §2.4)。 */
public interface TenantEvents {

    record TenantCreated(TenantId tenantId, String slug) implements DomainEvent {}
}
