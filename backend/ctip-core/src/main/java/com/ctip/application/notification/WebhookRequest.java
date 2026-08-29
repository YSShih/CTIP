package com.ctip.application.notification;

import com.ctip.domain.notification.NotificationType;
import java.util.UUID;

/**
 * 一次送達的完整 HTTP 請求描述(docs/spec/13-platform-ops.md §13.2 的五個送達標頭)。
 * 簽章已在 application 層以聚合的密鑰算好——{@link com.ctip.application.port.WebhookSenderPort}
 * 的實作只負責送出,不碰密鑰。
 *
 * @param signature {@code X-CTIP-Signature} 的完整值({@code sha256=<hex>})
 * @param timestamp {@code X-CTIP-Timestamp}(epoch 秒);同時是簽章 payload 的前綴
 */
public record WebhookRequest(
        String targetUrl,
        String signature,
        UUID eventId,
        NotificationType eventType,
        int attempt,
        long timestamp,
        byte[] body) {}
