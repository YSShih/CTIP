package com.ctip.domain.stix;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctip.domain.indicator.IocValue;
import com.ctip.sdk.IocHashType;
import com.ctip.sdk.IocType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * 六種 IocType 的 pattern 模板與四種 hash 演算法對應(docs/spec/07-domain-intel.md §7.8.3);
 * 值取 normalizedValue 並經跳脫。
 */
@Tag("unit")
class StixPatternTest {

    @Test
    void sixTypeTemplatesMatchSpecExamples() {
        assertThat(pattern(IocType.IPV4, null, "203.0.113.1")).isEqualTo("[ipv4-addr:value = '203.0.113.1']");
        assertThat(pattern(IocType.IPV6, null, "2001:db8::1")).isEqualTo("[ipv6-addr:value = '2001:db8::1']");
        assertThat(pattern(IocType.DOMAIN, null, "evil.example.com"))
                .isEqualTo("[domain-name:value = 'evil.example.com']");
        assertThat(pattern(IocType.URL, null, "https://evil.example.com/a"))
                .isEqualTo("[url:value = 'https://evil.example.com/a']");
        assertThat(pattern(IocType.EMAIL, null, "a@evil.example.com"))
                .isEqualTo("[email-addr:value = 'a@evil.example.com']");
        assertThat(pattern(
                        IocType.FILE_HASH,
                        IocHashType.SHA256,
                        "2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae"))
                .isEqualTo(
                        "[file:hashes.'SHA-256' = '2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae']");
    }

    @Test
    void hashAlgorithmMappingUsesHyphenatedOpenVocabulary() {
        assertThat(StixPatternBuilder.hashKey(IocHashType.MD5)).isEqualTo("MD5");
        assertThat(StixPatternBuilder.hashKey(IocHashType.SHA1)).isEqualTo("SHA-1");
        assertThat(StixPatternBuilder.hashKey(IocHashType.SHA256)).isEqualTo("SHA-256");
        assertThat(StixPatternBuilder.hashKey(IocHashType.SHA512)).isEqualTo("SHA-512");
    }

    @Test
    void urlContainingQuoteIsEscaped() {
        assertThat(pattern(IocType.URL, null, "https://evil.example.com/it's-a-trap"))
                .isEqualTo("[url:value = 'https://evil.example.com/it\\'s-a-trap']");
    }

    @Test
    void backslashIsEscapedBeforeQuote() {
        assertThat(StixPatternEscaper.escape("a\\'b")).isEqualTo("a\\\\\\'b");
        assertThat(StixPatternEscaper.escape("plain")).isEqualTo("plain");
    }

    private static String pattern(IocType type, IocHashType hashType, String normalized) {
        return StixPatternBuilder.pattern(new IocValue(type, hashType, normalized, normalized));
    }
}
