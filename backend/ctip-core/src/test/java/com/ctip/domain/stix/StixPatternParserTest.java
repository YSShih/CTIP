package com.ctip.domain.stix;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctip.domain.indicator.IocValue;
import com.ctip.sdk.IocHashType;
import com.ctip.sdk.IocType;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * STIX pattern 的反解(§7.8.3 六個固定模板的反向;匯入 bundle 用)。
 * 與 {@link StixPatternBuilder} 互為驗證:本平台匯出的 bundle 必須能原樣再匯入。
 */
@Tag("unit")
class StixPatternParserTest {

    static Stream<IocValue> roundTripCases() {
        return Stream.of(
                new IocValue(IocType.IPV4, null, "203.0.113.5", "203.0.113.5"),
                new IocValue(IocType.IPV6, null, "2001:db8::1", "2001:db8::1"),
                new IocValue(IocType.DOMAIN, null, "evil.example.org", "evil.example.org"),
                new IocValue(IocType.URL, null, "https://evil.example.org/a", "https://evil.example.org/a"),
                new IocValue(IocType.EMAIL, null, "phish@example.org", "phish@example.org"),
                new IocValue(
                        IocType.FILE_HASH,
                        IocHashType.SHA256,
                        "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                        "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"));
    }

    @ParameterizedTest
    @MethodSource("roundTripCases")
    void everyBuilderTemplateParsesBack(IocValue value) {
        var parsed = StixPatternParser.parse(StixPatternBuilder.pattern(value)).orElseThrow();

        assertThat(parsed.type()).isEqualTo(value.type());
        assertThat(parsed.hashType()).isEqualTo(value.hashType());
        assertThat(parsed.value()).isEqualTo(value.normalized());
    }

    /** 跳脫過的引號與反斜線必須還原,否則匯入回來的值與匯出的不同。 */
    @Test
    void escapedCharactersAreRestored() {
        IocValue tricky =
                new IocValue(IocType.URL, null, "https://x.example.org/a'b\\c", "https://x.example.org/a'b\\c");

        var parsed = StixPatternParser.parse(StixPatternBuilder.pattern(tricky)).orElseThrow();

        assertThat(parsed.value()).isEqualTo("https://x.example.org/a'b\\c");
    }

    /** 認不得的 pattern 回 empty——猜測式解析會把不是 IOC 的字串寫進資料庫。 */
    @Test
    void unsupportedPatternsAreNotGuessed() {
        assertThat(StixPatternParser.parse("[ipv4-addr:value = '1.1.1.1'] AND [url:value = 'x']"))
                .isEmpty();
        assertThat(StixPatternParser.parse("[process:pid = 4]")).isEmpty();
        assertThat(StixPatternParser.parse("[file:hashes.'CRC32' = 'deadbeef']"))
                .isEmpty();
        assertThat(StixPatternParser.parse(null)).isEmpty();
    }
}
