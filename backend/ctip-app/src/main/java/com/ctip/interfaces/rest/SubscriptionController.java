package com.ctip.interfaces.rest;

import com.ctip.application.identity.ApiKeyService;
import com.ctip.application.plan.QuotaService;
import com.ctip.domain.plan.Plan;
import com.ctip.domain.tenant.TenantId;
import com.ctip.infrastructure.security.TenantContext;
import com.ctip.interfaces.rest.dto.subscription.SubscriptionDto;
import com.ctip.interfaces.rest.dto.subscription.SubscriptionUsageDto;
import com.ctip.interfaces.rest.mapper.SubscriptionDtoMapper;
import com.ctip.interfaces.rest.openapi.SubscriptionApi;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 訂閱端點(docs/spec/09-api.md §9.1)。
 * 租戶範圍取自 {@link TenantContext},呼叫端不得指定——沒有「查別人的方案」這件事。
 */
@RestController
@RequestMapping("/api/v1/subscription")
class SubscriptionController implements SubscriptionApi {

    private final QuotaService quotas;
    private final ApiKeyService apiKeys;
    private final TenantContext tenantContext;
    private final SubscriptionDtoMapper mapper;

    SubscriptionController(
            QuotaService quotas, ApiKeyService apiKeys, TenantContext tenantContext, SubscriptionDtoMapper mapper) {
        this.quotas = quotas;
        this.apiKeys = apiKeys;
        this.tenantContext = tenantContext;
        this.mapper = mapper;
    }

    @Override
    @GetMapping
    @PreAuthorize("hasAuthority('subscription:read')")
    public SubscriptionDto subscription() {
        TenantId tenantId = tenantContext.tenantId();
        return mapper.toDto(quotas.planFor(tenantId), quotas.subscriptionOf(tenantId));
    }

    @Override
    @GetMapping("/usage")
    @PreAuthorize("hasAuthority('subscription:read')")
    public SubscriptionUsageDto subscriptionUsage() {
        TenantId tenantId = tenantContext.tenantId();
        Plan plan = quotas.planFor(tenantId);
        return mapper.toUsage(plan, quotas.manualSubmissionUsage(tenantId), apiKeys.countActive(tenantId));
    }
}
