package com.ctip.application.admin;

import com.ctip.domain.plan.PlanCode;
import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.tenant.TenantStatus;
import com.ctip.domain.tenant.TenantType;

/** 管理端點的租戶一列:租戶本體 + 目前生效方案(docs/spec/09-api.md §9.1「管理」)。 */
public record TenantOverview(
        TenantId id, String slug, String name, TenantType type, TenantStatus status, PlanCode planCode) {}
