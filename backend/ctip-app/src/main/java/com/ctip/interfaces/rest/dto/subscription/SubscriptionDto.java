package com.ctip.interfaces.rest.dto.subscription;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * 目前生效的方案與訂閱(docs/spec/09-api.md §9.1 {@code GET /api/v1/subscription})。
 *
 * <p>沒有訂閱的已登入租戶回 {@code FREE}(不變量 B4),此時 {@code status} / {@code provider} /
 * 期間欄位皆為 null——那是「沒有訂閱列」與「有一份 FREE 訂閱」的差別,不該被抹平。
 */
public record SubscriptionDto(
        @Schema(example = "PREMIUM") String planCode,
        @Schema(example = "Premium") String planName,
        @Schema(example = "2") int tier,
        @Schema(example = "ACTIVE") String status,
        @Schema(example = "MANUAL") String provider,
        @Schema(example = "2026-08-01T00:00:00Z") Instant currentPeriodStart,
        @Schema(example = "2027-08-01T00:00:00Z") Instant currentPeriodEnd,
        @Schema(example = "null") Instant cancelledAt,
        PlanQuotasDto quotas) {}
