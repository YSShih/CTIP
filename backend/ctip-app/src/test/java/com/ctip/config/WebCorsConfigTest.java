package com.ctip.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** CORS origin 清單解析:逗號後帶空白的慣用寫法不得使項目靜默失效。 */
@Tag("unit")
class WebCorsConfigTest {

    @Test
    void parseOriginsTrimsEntriesAndDropsEmptyOnes() {
        assertThat(WebCorsConfig.parseOrigins("https://a.example.com, https://b.example.com ,,http://c.example.com"))
                .containsExactly("https://a.example.com", "https://b.example.com", "http://c.example.com");
        assertThat(WebCorsConfig.parseOrigins("http://localhost:5173,http://127.0.0.1:5173"))
                .containsExactly("http://localhost:5173", "http://127.0.0.1:5173");
    }
}
