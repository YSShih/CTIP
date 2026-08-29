package com.ctip.interfaces.webhook;

import com.ctip.application.notification.WebhookRequest;
import com.ctip.application.notification.WebhookSendResult;
import com.ctip.application.port.ClockPort;
import com.ctip.application.port.WebhookSenderPort;
import com.ctip.config.CtipProperties;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * {@link WebhookSenderPort} 的 JDK {@code HttpClient} 實作(§13.2 的送達標頭與簽章)。
 *
 * <p>不跟隨轉址:{@code 3xx} 一律當成失敗。跟隨轉址等於讓接收端把帶著有效簽章的請求
 * 導去任意主機,而簽章對象裡沒有目標 URL——那是一個 request forgery 的放大器。
 *
 * <p>逾時來自 {@code ctip.notification.delivery-timeout-seconds}:沒有逾時的送達會在
 * 程序內通知路徑上把整個扇出卡住。
 */
@Component
class HttpWebhookSender implements WebhookSenderPort {

    private final HttpClient client;
    private final Duration timeout;
    private final ClockPort clock;

    HttpWebhookSender(CtipProperties properties, ClockPort clock) {
        this.timeout = Duration.ofSeconds(properties.notification().deliveryTimeoutSeconds());
        this.clock = clock;
        this.client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(this.timeout)
                .build();
    }

    @Override
    public WebhookSendResult send(WebhookRequest request) {
        long startedAt = clock.now().toEpochMilli();
        try {
            HttpResponse<Void> response =
                    client.send(WebhookHttpRequests.build(request, timeout), HttpResponse.BodyHandlers.discarding());
            int elapsed = elapsedMillis(startedAt);
            return response.statusCode() / 100 == 2
                    ? WebhookSendResult.ok(response.statusCode(), elapsed)
                    : WebhookSendResult.rejected(response.statusCode(), elapsed);
        } catch (IOException e) {
            // 訊息只留型別與簡述:它會落進 append-only 的送達記錄,而例外訊息可能含目標主機的細節
            return WebhookSendResult.failed(
                    elapsedMillis(startedAt), e.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return WebhookSendResult.failed(elapsedMillis(startedAt), "interrupted");
        }
    }

    private int elapsedMillis(long startedAt) {
        return (int) Math.max(0, clock.now().toEpochMilli() - startedAt);
    }
}
