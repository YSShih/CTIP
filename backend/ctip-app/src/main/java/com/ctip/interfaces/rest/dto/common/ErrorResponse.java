package com.ctip.interfaces.rest.dto.common;

import java.time.Instant;
import java.util.List;

/** 統一錯誤回應(docs/spec/09-api.md §9.4)。message 為英文;UI 文案由前端依 code 對映。 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        String traceId,
        List<FieldIssue> details) {

    public record FieldIssue(String field, String issue) {}
}
