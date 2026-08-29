package com.ctip.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * 信任的反向代理來源(docs/spec/10-identity-plans.md §10.7)。
 * 預設為空 = 誰都不信:任何 client 都能自己送 X-Forwarded-For,採信它等於 IP 維度不存在。
 */
@Tag("unit")
class TrustedProxiesTest {

    @Test
    void emptyListTrustsNobody() {
        TrustedProxies proxies = new TrustedProxies(List.of());
        assertThat(proxies.isEmpty()).isTrue();
        assertThat(proxies.contains("10.0.0.1")).isFalse();
    }

    @Test
    void cidrAndSingleAddressBothMatch() {
        TrustedProxies proxies = new TrustedProxies(List.of("10.0.0.0/8", "192.168.1.10"));
        assertThat(proxies.contains("10.4.5.6")).isTrue();
        assertThat(proxies.contains("192.168.1.10")).isTrue();
        assertThat(proxies.contains("192.168.1.11")).isFalse();
        assertThat(proxies.contains("203.0.113.7")).isFalse();
    }

    /** 逗號分隔的設定值會帶空白;空項目不得變成「信任所有人」。 */
    @Test
    void blankEntriesAreIgnored() {
        assertThat(new TrustedProxies(List.of(" 10.0.0.0/8 ", "")).contains("10.1.2.3"))
                .isTrue();
    }

    /** 格式不合的對端位址代表「不匹配」,不該讓請求失敗。 */
    @Test
    void malformedRemoteAddressIsNotTrusted() {
        TrustedProxies proxies = new TrustedProxies(List.of("10.0.0.0/8"));
        assertThat(proxies.contains("not-an-ip")).isFalse();
        assertThat(proxies.contains(null)).isFalse();
    }
}
