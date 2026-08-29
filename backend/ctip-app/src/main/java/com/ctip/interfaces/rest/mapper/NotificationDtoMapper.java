package com.ctip.interfaces.rest.mapper;

import com.ctip.application.notification.NotificationRecord;
import com.ctip.domain.notification.Webhook;
import com.ctip.interfaces.rest.dto.notification.NotificationDto;
import com.ctip.interfaces.rest.dto.notification.WebhookDto;
import com.ctip.interfaces.rest.dto.notification.WebhookFilterDto;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.mapstruct.Mapper;

/**
 * 通知與 webhook → DTO。
 *
 * <p>{@link WebhookDto} <strong>沒有</strong>密鑰欄位:原文只在建立當下回傳一次
 * (不變量 W2 的對外契約),之後任何讀取端點都不得再吐出它。
 */
@Mapper(componentModel = "spring")
public interface NotificationDtoMapper {

    default NotificationDto toDto(NotificationRecord notification) {
        return new NotificationDto(
                notification.id(),
                notification.eventType().name(),
                notification.title(),
                notification.body(),
                notification.severity().name(),
                notification.resourceType(),
                notification.resourceId(),
                notification.readAt() != null,
                notification.createdAt());
    }

    default WebhookDto toDto(Webhook webhook) {
        return new WebhookDto(
                webhook.id().value(),
                webhook.name(),
                webhook.targetUrl(),
                webhook.eventTypes().stream().map(Enum::name).sorted().toList(),
                toDto(webhook.filter()),
                webhook.status().name(),
                webhook.consecutiveFailures(),
                webhook.lastDeliveryAt(),
                webhook.lastSuccessAt(),
                webhook.createdAt());
    }

    /** 集合一律排序輸出:回應內容不該隨 HashSet 的迭代順序漂移。 */
    private static WebhookFilterDto toDto(com.ctip.domain.notification.WebhookFilter filter) {
        List<UUID> sourceIds = filter.sourceIds().stream()
                .sorted(Comparator.comparing(UUID::toString))
                .toList();
        return new WebhookFilterDto(
                filter.iocTypes().stream().map(Enum::name).sorted().toList(),
                filter.minSeverity() == null ? null : filter.minSeverity().name(),
                filter.tags().stream().sorted().toList(),
                sourceIds);
    }
}
