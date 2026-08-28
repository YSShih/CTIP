package com.ctip.interfaces.rest.dto.ioc;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * 匯入 job 的狀態(docs/spec/04-data-dictionary.md 表 18b;§9.7 的 202 回應與進度查詢)。
 * 逐筆的拒絕明細在 {@code ingestion_rejections},本回應只給各類計數。
 */
public record ImportJobDto(
        @Schema(example = "0f2d7b3c-9a41-4a7e-8b2f-1c5d6e7f8a90")
        UUID importJobId,

        @Schema(example = "RUNNING") String status,
        @Schema(example = "CSV") String format,
        @Schema(example = "1200") Integer totalRows,
        @Schema(example = "1150") int acceptedCount,
        @Schema(example = "40") int mergedCount,
        @Schema(example = "10") int rejectedCount,
        @Schema(example = "null") String errorMessage,
        @Schema(example = "2026-08-28T09:00:00Z") Instant startedAt,
        @Schema(example = "null") Instant finishedAt,
        @Schema(example = "2026-08-28T09:00:00Z") Instant createdAt) {}
