package com.ctip.support;

import com.ctip.application.notification.WebhookRequest;
import com.ctip.application.notification.WebhookSendResult;
import com.ctip.application.port.WebhookSenderPort;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.IntFunction;

/**
 * 測試用的送達端:記錄每一次請求,回應可程式化。
 *
 * <p>不用真的 HTTP 伺服器,因為不變量 W1 要求 {@code targetUrl} 必須是 {@code https://}
 * ——在測試裡起一個帶合法憑證的 HTTPS 伺服器,量到的是 JDK 的 TLS 而不是本專案的行為。
 * 真正的線上格式(五個 {@code X-CTIP-*} 標頭與請求組裝)由 {@code HttpWebhookSenderTest}
 * 直接對 {@code HttpRequest} 斷言。
 */
public class RecordingWebhookSender implements WebhookSenderPort {

    private final List<WebhookRequest> requests = Collections.synchronizedList(new ArrayList<>());

    private volatile IntFunction<WebhookSendResult> responder = attempt -> WebhookSendResult.ok(200, 3);

    @Override
    public WebhookSendResult send(WebhookRequest request) {
        requests.add(request);
        return responder.apply(request.attempt());
    }

    public void succeedAlways() {
        responder = attempt -> WebhookSendResult.ok(200, 3);
    }

    public void failAlways() {
        responder = attempt -> WebhookSendResult.rejected(500, 3);
    }

    /** 逐次指定結果,供「先失敗再成功」之類的情境使用。 */
    public void respondWith(IntFunction<WebhookSendResult> responder) {
        this.responder = responder;
    }

    public List<WebhookRequest> requests() {
        return List.copyOf(requests);
    }

    public void reset() {
        requests.clear();
        succeedAlways();
    }
}
