package com.ctip.interfaces.rest.dto.subscription;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * 目前用量(docs/spec/09-api.md §9.1 {@code GET /api/v1/subscription/usage})。
 *
 * <p>只列<strong>本里程碑真的有計數來源</strong>的兩項:每日手動提交(限流器的日視窗)
 * 與有效 API key 數量。webhook 數量要到 Phase 20 才有 {@code webhooks} 表,
 * 現在填 0 會是騙人的數字(規則 16),故不列。
 */
public record SubscriptionUsageDto(
        @Schema(example = "PREMIUM") String planCode, UsageItem manualSubmissionsToday, UsageItem apiKeys) {

    /**
     * @param used 已使用量
     * @param limit 上限;{@code null} = 無限制、{@code 0} = 停用
     * @param resetAt 視窗重置時間;非時間窗的項目為 null
     */
    public record UsageItem(
            @Schema(example = "12") long used,
            @Schema(example = "1000") Long limit,
            @Schema(example = "2026-08-29T00:00:00Z") Instant resetAt) {}
}
