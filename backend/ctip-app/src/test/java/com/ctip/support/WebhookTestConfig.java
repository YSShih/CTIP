package com.ctip.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 以可觀察的送達端取代真正的 HTTP 送出。
 *
 * <p>三個 webhook 相關的整合測試共用同一份 {@code @Import},因此共用同一個 Spring context
 * ——測試 context 的快取鍵包含 {@code @Import} 的內容,分開宣告會多起兩個 context。
 */
@TestConfiguration
public class WebhookTestConfig {

    @Bean
    @Primary
    public RecordingWebhookSender recordingWebhookSender() {
        return new RecordingWebhookSender();
    }
}
