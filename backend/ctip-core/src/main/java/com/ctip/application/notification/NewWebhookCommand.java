package com.ctip.application.notification;

import com.ctip.domain.notification.NotificationType;
import com.ctip.domain.notification.WebhookFilter;
import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.user.UserId;
import java.util.Set;

/**
 * 建立 webhook 的輸入(09 §9.1 的 {@code POST /webhooks})。
 * 歸屬與建立者<strong>不由呼叫端的 request body 決定</strong>——controller 從
 * {@code TenantContext} 取,body 沒有這兩個欄位。
 */
public record NewWebhookCommand(
        TenantId tenantId,
        UserId createdBy,
        String name,
        String targetUrl,
        Set<NotificationType> eventTypes,
        WebhookFilter filter) {}
