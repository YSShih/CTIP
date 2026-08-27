package com.ctip.domain.event;

import com.ctip.domain.identity.ApiKeyId;
import com.ctip.domain.tenant.TenantId;

/** ApiKey 聚合發佈的 M2 事件(docs/spec/02-ddd-model.md §2.4)。原文與雜湊皆不進入事件。 */
public interface ApiKeyEvents {

    record ApiKeyCreated(TenantId tenantId, ApiKeyId apiKeyId) implements DomainEvent {}

    record ApiKeyRevoked(TenantId tenantId, ApiKeyId apiKeyId) implements DomainEvent {}
}
