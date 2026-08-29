package com.ctip.interfaces.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctip.application.notification.WebhookRequest;
import com.ctip.domain.notification.NotificationType;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * §13.2 的五個送達標頭。它們是對外契約——接收端照這幾個字串驗簽,
 * 改名等於讓所有既有的接收端一起失效。
 */
@Tag("unit")
class WebhookHttpRequestsTest {

    private static final UUID EVENT = UUID.fromString("7c3f1a20-0000-4000-8000-00000000beef");

    @Test
    void buildsThePostWithTheFiveCtipHeaders() {
        HttpRequest request = WebhookHttpRequests.build(
                new WebhookRequest(
                        "https://hooks.ctip-sample.invalid/soc",
                        "sha256=deadbeef",
                        EVENT,
                        NotificationType.NEW_IOC,
                        3,
                        1755763200L,
                        "{\"eventId\":\"x\"}".getBytes(StandardCharsets.UTF_8)),
                Duration.ofSeconds(10));

        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.uri().toString()).isEqualTo("https://hooks.ctip-sample.invalid/soc");
        assertThat(header(request, WebhookHeaders.SIGNATURE)).isEqualTo("sha256=deadbeef");
        assertThat(header(request, WebhookHeaders.EVENT_ID)).isEqualTo(EVENT.toString());
        assertThat(header(request, WebhookHeaders.EVENT_TYPE)).isEqualTo("NEW_IOC");
        assertThat(header(request, WebhookHeaders.DELIVERY_ATTEMPT)).isEqualTo("3");
        assertThat(header(request, WebhookHeaders.TIMESTAMP)).isEqualTo("1755763200");
        assertThat(header(request, "Content-Type")).startsWith("application/json");
        assertThat(request.timeout()).contains(Duration.ofSeconds(10));
    }

    /** 標頭名稱本身是契約,寫死在這裡當作第二份來源。 */
    @Test
    void theHeaderNamesAreExactlyThoseInTheSpecification() {
        assertThat(WebhookHeaders.SIGNATURE).isEqualTo("X-CTIP-Signature");
        assertThat(WebhookHeaders.EVENT_ID).isEqualTo("X-CTIP-Event-Id");
        assertThat(WebhookHeaders.EVENT_TYPE).isEqualTo("X-CTIP-Event-Type");
        assertThat(WebhookHeaders.DELIVERY_ATTEMPT).isEqualTo("X-CTIP-Delivery-Attempt");
        assertThat(WebhookHeaders.TIMESTAMP).isEqualTo("X-CTIP-Timestamp");
    }

    private static String header(HttpRequest request, String name) {
        return request.headers().firstValue(name).orElseThrow(() -> new AssertionError("缺少標頭 " + name));
    }
}
