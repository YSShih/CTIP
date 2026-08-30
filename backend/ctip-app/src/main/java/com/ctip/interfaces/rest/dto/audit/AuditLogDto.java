package com.ctip.interfaces.rest.dto.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 一列稽核軌跡(docs/spec/09-api.md §9.1 的 {@code GET /audit-logs};04 表 27)。
 *
 * <p>{@code metadata} 不含憑證、token 原文或密碼——寫入端已在 {@code AuditMetadata} 過濾一次
 * (§13.5 規則 5),此處原樣輸出。
 */
public record AuditLogDto(
        UUID id,
        Instant occurredAt,
        String actorType,
        UUID actorId,
        String action,
        String resourceType,
        UUID resourceId,
        String ip,
        String userAgent,
        String result,
        String traceId,
        Map<String, Object> metadata) {}
