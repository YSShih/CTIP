package com.ctip.interfaces.rest.dto.ioc;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 誤判回報(docs/spec/09-api.md §9.7)。最終狀態由合併規則決定,呼叫端不得指定狀態。
 * reason 與 evidenceUrl 落在該來源記錄的 {@code raw_payload}(04 表 5 沒有為它們開欄位)。
 */
public record FalsePositiveRequest(
        @NotBlank @Size(max = 1024) @Schema(example = "legitimate CDN endpoint")
        String reason,

        @Size(max = 2048) @Schema(example = "null") String evidenceUrl) {}
