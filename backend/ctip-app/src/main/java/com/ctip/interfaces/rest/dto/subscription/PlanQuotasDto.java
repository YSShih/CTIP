package com.ctip.interfaces.rest.dto.subscription;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 方案的配額值(docs/spec/10-identity-plans.md §10.6 的 14 個維度)。
 *
 * <p>數值欄位一律用包裝型別:{@code null} = 無限制、{@code 0} = 停用。用原始型別會把
 * ENTERPRISE 的「依合約」印成 0,把無限制變成完全不能用(ADR 0019)。
 */
public record PlanQuotasDto(
        @Schema(example = "1200") Long requestsPerMinute,
        @Schema(example = "500000") Long requestsPerDay,
        @Schema(example = "500") int maxPageSize,
        @Schema(example = "1000") Long maxBatchLookup,
        @Schema(example = "300") int minSyncIntervalSeconds,
        @Schema(example = "true") boolean publicBloomEnabled,
        @Schema(example = "1000000") Long tenantBloomCapacity,
        @Schema(example = "true") boolean websocketEnabled,
        @Schema(example = "5") Long maxWebhooks,
        @Schema(example = "10") Long maxApiKeys,
        @Schema(example = "false") boolean customFeedEnabled,
        @Schema(example = "50000") Long stixExportMaxObjects,
        @Schema(example = "1000") Long maxManualSubmissionsPerDay,
        @Schema(example = "10000") Long maxImportRowsPerFile) {}
