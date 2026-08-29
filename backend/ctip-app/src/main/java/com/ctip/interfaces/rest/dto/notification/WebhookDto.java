package com.ctip.interfaces.rest.dto.notification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Webhook 設定(docs/spec/09-api.md §9.1 的 {@code GET /webhooks})。
 * <strong>不含簽章密鑰</strong>:原文只在建立當下回傳一次(不變量 W2 的對外契約)。
 */
public record WebhookDto(
        UUID id,
        String name,
        String targetUrl,
        List<String> eventTypes,
        WebhookFilterDto filter,
        String status,
        int consecutiveFailures,
        Instant lastDeliveryAt,
        Instant lastSuccessAt,
        Instant createdAt) {}
