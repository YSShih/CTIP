package com.ctip.interfaces.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * SSRF 防線二:送達前對<strong>解析後</strong>的位址判定。
 *
 * <p>建立時的字串檢查擋不掉「主機名解析到內網」與 DNS rebinding——目標的 A 記錄可以在通過
 * 建立檢查之後才改指到 {@code 169.254.169.254}。這裡只用字面位址,測試不依賴外部 DNS。
 */
@Tag("unit")
class WebhookTargetGuardTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "https://127.0.0.1/hook",
                "https://localhost/hook",
                "https://169.254.169.254/latest/meta-data/",
                "https://10.1.2.3/hook",
                "https://192.168.0.1/hook",
                "https://[::1]/hook",
                "https://[fd00::1]/hook"
            })
    void targetsResolvingIntoThePlatformNetworkAreNotSent(String url) {
        assertThat(WebhookTargetGuard.isPubliclyRoutable(url)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://8.8.8.8/hook", "https://[2001:4860:4860::8888]/hook"})
    void publiclyRoutableTargetsAreSent(String url) {
        assertThat(WebhookTargetGuard.isPubliclyRoutable(url)).isTrue();
    }

    /** 解不出主機就送不出去;當成「不可送」比交給 HttpClient 再解析一次好(那一次不經本檢查)。 */
    @ParameterizedTest
    @ValueSource(strings = {"https://ctip-nonexistent.invalid/hook", "not a url", "https:///hook"})
    void unresolvableOrMalformedTargetsAreNotSent(String url) {
        assertThat(WebhookTargetGuard.isPubliclyRoutable(url)).isFalse();
    }
}
