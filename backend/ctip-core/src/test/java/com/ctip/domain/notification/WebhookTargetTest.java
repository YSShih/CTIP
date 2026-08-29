package com.ctip.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 不變量 W1 的完整形式(SSRF 防線一)。
 *
 * <p>送達是「伺服器主動對租戶指定的 URL 發 POST」。只驗 {@code https://} 的話,任何持
 * {@code webhook:manage} 的租戶都能把平台變成內網掃描器與雲端 metadata 的取用管道。
 */
@Tag("unit")
class WebhookTargetTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "https://127.0.0.1/hook",
                "https://127.9.9.9:8443/hook",
                "https://localhost/hook",
                "https://api.localhost/hook",
                "https://redis.internal/hook",
                "https://169.254.169.254/latest/meta-data/", // 雲端 metadata
                "https://10.0.0.5:8080/admin",
                "https://172.20.1.1/hook",
                "https://192.168.1.1/hook",
                "https://100.64.0.1/hook", // CGNAT
                "https://0.0.0.0/hook",
                "https://255.255.255.255/hook",
                "https://[::1]/hook",
                "https://[fd00::1]/hook", // ULA
                "https://[fe80::1]/hook", // link-local
                "https://[::ffff:127.0.0.1]/hook" // IPv4-mapped
            })
    void targetsInsideThePlatformNetworkAreRejected(String url) {
        assertThatThrownBy(() -> WebhookTarget.require(url))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("W1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://hooks.example.invalid/x", "ftp://hooks.example.invalid/x", "hooks.example/x"})
    void nonHttpsSchemesAreRejected(String url) {
        assertThatThrownBy(() -> WebhookTarget.require(url)).isInstanceOf(IllegalArgumentException.class);
    }

    /** URL 內嵌帳密會原樣落進 {@code webhooks.target_url},等於把憑證以明文存進資料庫。 */
    @Test
    void embeddedCredentialsAreRejected() {
        assertThatThrownBy(() -> WebhookTarget.require("https://user:pass@hooks.example.invalid/x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "https://hooks.example.invalid/x",
                "https://8.8.8.8/hook",
                "https://hooks.example.invalid:8443/x?a=b",
                "https://[2001:4860:4860::8888]/hook"
            })
    void publiclyRoutableTargetsAreAccepted(String url) {
        assertThatCode(() -> WebhookTarget.require(url)).doesNotThrowAnyException();
        assertThat(WebhookTarget.require(url)).isEqualTo(url);
    }

    /** 空字串與 null 走同一條路徑,不得變成 NPE(那會是 500 而不是 400)。 */
    @Test
    void blankTargetsAreRejectedAsInvalidInput() {
        assertThatThrownBy(() -> WebhookTarget.require(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WebhookTarget.require("  ")).isInstanceOf(IllegalArgumentException.class);
    }
}
