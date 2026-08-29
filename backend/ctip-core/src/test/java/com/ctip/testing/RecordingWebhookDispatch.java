package com.ctip.testing;

import com.ctip.application.notification.NotificationRecord;
import com.ctip.application.notification.WebhookRequest;
import com.ctip.application.notification.WebhookSendResult;
import com.ctip.application.port.RealtimePushPort;
import com.ctip.application.port.WebhookPayloadPort;
import com.ctip.application.port.WebhookSenderPort;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

/** 送達端、payload 序列化與即時推播的測試替身(三者都只是 application 單元測試的觀察點)。 */
public final class RecordingWebhookDispatch implements WebhookSenderPort, WebhookPayloadPort, RealtimePushPort {

    private final List<WebhookRequest> requests = new ArrayList<>();
    private final List<NotificationRecord> pushed = new ArrayList<>();

    private IntFunction<WebhookSendResult> responder = attempt -> WebhookSendResult.ok(200, 1);

    @Override
    public WebhookSendResult send(WebhookRequest request) {
        requests.add(request);
        return responder.apply(request.attempt());
    }

    @Override
    public byte[] body(NotificationRecord notification) {
        return ("{\"eventId\":\"" + notification.eventId() + "\"}").getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void push(NotificationRecord notification) {
        pushed.add(notification);
    }

    public void failAlways() {
        responder = attempt -> WebhookSendResult.rejected(500, 1);
    }

    public void respondWith(IntFunction<WebhookSendResult> responder) {
        this.responder = responder;
    }

    public List<WebhookRequest> requests() {
        return List.copyOf(requests);
    }

    public List<NotificationRecord> pushed() {
        return List.copyOf(pushed);
    }
}
