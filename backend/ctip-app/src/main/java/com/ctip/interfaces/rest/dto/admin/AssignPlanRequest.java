package com.ctip.interfaces.rest.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 指派租戶方案({@code PATCH /api/v1/admin/tenants/{id}/subscription})。
 * {@code planCode} 為 {@code CANCEL} 時代表取消目前訂閱(04 表 18 的 B3:取消後不得回到 ACTIVE)。
 */
public record AssignPlanRequest(
        @NotBlank
        @Schema(
                example = "PREMIUM",
                allowableValues = {"FREE", "PREMIUM", "ENTERPRISE", "CANCEL"})
        String planCode) {}
