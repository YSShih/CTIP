package com.ctip.interfaces.rest.dto.admin;

import java.util.UUID;

/** 管理端點的租戶一列(docs/spec/09-api.md §9.1「管理」的 {@code GET /admin/tenants})。 */
public record TenantOverviewDto(UUID id, String slug, String name, String type, String status, String planCode) {}
