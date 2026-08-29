package com.ctip.interfaces.webhook;

import com.ctip.application.notification.WebhookRequest;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;

/**
 * 送達請求的組裝(docs/spec/13-platform-ops.md §13.2 的五個標頭)。
 *
 * <p>獨立成一個純函數,測試才能在<strong>不真的送出</strong>的情況下驗標頭
 * ——不變量 W1 要求 https,在測試裡起一個帶合法憑證的 HTTPS 伺服器量到的是 JDK 的 TLS,
 * 不是本專案的線上格式。
 */
final class WebhookHttpRequests {

    private WebhookHttpRequests() {}

    static HttpRequest build(WebhookRequest request, Duration timeout) {
        return HttpRequest.newBuilder(URI.create(request.targetUrl()))
                .timeout(timeout)
                .header("Content-Type", "application/json; charset=utf-8")
                .header(WebhookHeaders.SIGNATURE, request.signature())
                .header(WebhookHeaders.EVENT_ID, request.eventId().toString())
                .header(WebhookHeaders.EVENT_TYPE, request.eventType().name())
                .header(WebhookHeaders.DELIVERY_ATTEMPT, Integer.toString(request.attempt()))
                .header(WebhookHeaders.TIMESTAMP, Long.toString(request.timestamp()))
                .POST(HttpRequest.BodyPublishers.ofByteArray(request.body()))
                .build();
    }
}
