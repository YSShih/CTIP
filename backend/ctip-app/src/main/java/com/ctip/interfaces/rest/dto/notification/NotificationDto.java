package com.ctip.interfaces.rest.dto.notification;

import java.time.Instant;
import java.util.UUID;

/** 站內通知(docs/spec/09-api.md §9.1 的 {@code GET /notifications};04 表 26)。 */
public record NotificationDto(
        UUID id,
        String eventType,
        String title,
        String body,
        String severity,
        String resourceType,
        UUID resourceId,
        boolean read,
        Instant createdAt) {}
