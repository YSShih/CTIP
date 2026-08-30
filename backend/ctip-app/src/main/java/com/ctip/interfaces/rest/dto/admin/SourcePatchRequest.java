package com.ctip.interfaces.rest.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 調整來源設定({@code PATCH /api/v1/admin/sources/{id}})。
 *
 * <p>只有 {@code enabled}:那是 {@link com.ctip.domain.source.Source} 上唯一由外部意志決定的狀態,
 * 其餘欄位(健康度、游標、統計)是同步結果,由 ingestion 自己寫。
 */
public record SourcePatchRequest(
        @NotNull @Schema(example = "false") Boolean enabled) {}
