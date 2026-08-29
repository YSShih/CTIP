package com.ctip.application.notification;

import com.ctip.domain.notification.NotificationType;
import com.ctip.domain.tenant.TenantId;
import com.ctip.sdk.Severity;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code notifications} 的一列(docs/spec/04-data-dictionary.md 表 26)。
 * 該表是<strong>兩模型</strong>(01 §1.5:append-only 記錄,無跨欄位不變量),故沒有 domain model;
 * application 層以本 record 與 port 往來,JPA entity 留在 infrastructure。
 *
 * @param userId null = 全租戶廣播
 */
public record NotificationRecord(
        UUID id,
        TenantId tenantId,
        UUID userId,
        UUID eventId,
        NotificationType eventType,
        String title,
        String body,
        Severity severity,
        String resourceType,
        UUID resourceId,
        Instant readAt,
        Instant createdAt) {}
