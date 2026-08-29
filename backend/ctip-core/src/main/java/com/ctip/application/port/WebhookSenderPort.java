package com.ctip.application.port;

import com.ctip.application.notification.WebhookRequest;
import com.ctip.application.notification.WebhookSendResult;

/**
 * 送出一次 webhook HTTP 請求。實作在 {@code interfaces/webhook}(對外協定的邊界:
 * 五個 {@code X-CTIP-*} 標頭的實際寫法)。
 */
public interface WebhookSenderPort {

    WebhookSendResult send(WebhookRequest request);
}
