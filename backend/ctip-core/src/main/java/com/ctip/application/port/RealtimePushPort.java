package com.ctip.application.port;

import com.ctip.application.notification.NotificationRecord;

/**
 * 即時推送(09 §9.1「即時推送」:WebSocket 與 SSE fallback)。
 *
 * <p>實作在 {@code interfaces/websocket};連線綁 {@code tenantId},平台範圍的通知
 * (public tenant)推給所有連線,租戶自有的只推給該租戶——過濾在伺服器端。
 * 沒有任何連線時是 no-op:即時推送是盡力而為,站內通知已落庫,通知中心頁一定看得到。
 */
public interface RealtimePushPort {

    void push(NotificationRecord notification);
}
