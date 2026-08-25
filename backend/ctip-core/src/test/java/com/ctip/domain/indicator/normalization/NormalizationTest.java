package com.ctip.domain.indicator.normalization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ctip.sdk.IocType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * 正規化規則(docs/spec/07-domain-intel.md §7.2)表格每一列,含髒資料案例。
 * 指紋一律針對 normalize 輸出計算——normalize 的確定性是指紋正確性的前提。
 */
@Tag("unit")
class NormalizationTest {

    private final IocNormalizers normalizers = new IocNormalizers(false);

    @Nested
    class CommonCleaning {

        @Test
        void stripsWhitespaceZeroWidthAndControlCharacters() {
            assertThat(normalizers.clean("  evil.example.com  ")).isEqualTo("evil.example.com");
            assertThat(normalizers.clean("evil\u200b.exam\u200cple\u200d.com\ufeff"))
                    .isEqualTo("evil.example.com");
            assertThat(normalizers.clean("evil.example.com\r\n\t")).isEqualTo("evil.example.com");
        }
    }

    @Nested
    class Ipv4 {

        @Test
        void canonicalDottedDecimalWithoutLeadingZeros() {
            assertThat(normalizers.normalize(IocType.IPV4, "203.000.113.007")).isEqualTo("203.0.113.7");
            assertThat(normalizers.normalize(IocType.IPV4, "010.1.1.1")).isEqualTo("10.1.1.1");
        }

        @Test
        void rejectsInvalidAddresses() {
            assertThatThrownBy(() -> normalizers.normalize(IocType.IPV4, "999.1.2.3"))
                    .isInstanceOf(IocFormatException.class);
            assertThatThrownBy(() -> normalizers.normalize(IocType.IPV4, "1.2.3"))
                    .isInstanceOf(IocFormatException.class);
            assertThatThrownBy(() -> normalizers.normalize(IocType.IPV4, "1.2.3.x"))
                    .isInstanceOf(IocFormatException.class);
        }
    }

    @Nested
    class Ipv6 {

        @Test
        void compressesPerRfc5952() {
            assertThat(normalizers.normalize(IocType.IPV6, "2001:0DB8:0000:0000:0000:0000:0000:0001"))
                    .isEqualTo("2001:db8::1");
            // 兩段等長零段:取最左(RFC 5952 §4.2.3)
            assertThat(normalizers.normalize(IocType.IPV6, "2001:db8:0:0:1:0:0:1"))
                    .isEqualTo("2001:db8::1:0:0:1");
            assertThat(normalizers.normalize(IocType.IPV6, "::1")).isEqualTo("::1");
            assertThat(normalizers.normalize(IocType.IPV6, "::")).isEqualTo("::");
        }

        @Test
        void doesNotCompressSingleZeroGroup() {
            assertThat(normalizers.normalize(IocType.IPV6, "2001:db8:1:1:1:1:0:1"))
                    .isEqualTo("2001:db8:1:1:1:1:0:1");
        }

        @Test
        void parsesEmbeddedIpv4Tail() {
            assertThat(normalizers.normalize(IocType.IPV6, "::ffff:192.168.1.1"))
                    .isEqualTo("::ffff:c0a8:101");
        }

        @Test
        void rejectsMalformedIpv6() {
            assertThatThrownBy(() -> normalizers.normalize(IocType.IPV6, "1:::2"))
                    .isInstanceOf(IocFormatException.class);
            assertThatThrownBy(() -> normalizers.normalize(IocType.IPV6, "1::2::3"))
                    .isInstanceOf(IocFormatException.class);
            assertThatThrownBy(() -> normalizers.normalize(IocType.IPV6, "1:2:3:4:5:6:7"))
                    .isInstanceOf(IocFormatException.class);
            assertThatThrownBy(() -> normalizers.normalize(IocType.IPV6, "gggg::1"))
                    .isInstanceOf(IocFormatException.class);
        }
    }

    @Nested
    class Domain {

        @Test
        void lowercasesAndStripsTrailingDot() {
            assertThat(normalizers.normalize(IocType.DOMAIN, "Evil.ExAmple.COM."))
                    .isEqualTo("evil.example.com");
        }

        @Test
        void convertsIdnToPunycode() {
            assertThat(normalizers.normalize(IocType.DOMAIN, "bücher.example")).isEqualTo("xn--bcher-kva.example");
        }

        @Test
        void keepsWwwByDefaultAndStripsWhenConfigured() {
            assertThat(normalizers.normalize(IocType.DOMAIN, "www.example.com")).isEqualTo("www.example.com");
            IocNormalizers stripping = new IocNormalizers(true);
            assertThat(stripping.normalize(IocType.DOMAIN, "www.example.com")).isEqualTo("example.com");
        }

        @Test
        void rejectsInvalidDomains() {
            assertThatThrownBy(() -> normalizers.normalize(IocType.DOMAIN, "nodot"))
                    .isInstanceOf(IocFormatException.class);
            assertThatThrownBy(() -> normalizers.normalize(IocType.DOMAIN, "-bad.example.com"))
                    .isInstanceOf(IocFormatException.class);
            assertThatThrownBy(() -> normalizers.normalize(IocType.DOMAIN, "bad..example.com"))
                    .isInstanceOf(IocFormatException.class);
        }
    }

    @Nested
    class Url {

        @Test
        void lowercasesSchemeAndHostRemovesDefaultPortAndFragmentSortsQuery() {
            assertThat(normalizers.normalize(IocType.URL, "HTTPS://Upper.Example.COM:443/Path?b=2&a=1#frag"))
                    .isEqualTo("https://upper.example.com/Path?a=1&b=2");
        }

        @Test
        void keepsNonDefaultPortAndAddsRootPath() {
            assertThat(normalizers.normalize(IocType.URL, "http://example.com:8080"))
                    .isEqualTo("http://example.com:8080/");
            assertThat(normalizers.normalize(IocType.URL, "http://example.com:80/x"))
                    .isEqualTo("http://example.com/x");
        }

        @Test
        void normalizesPercentEncodingInPath() {
            // %41 = 'A'(非保留字元 → 解碼);%2f 保留但十六進位轉大寫
            assertThat(normalizers.normalize(IocType.URL, "https://example.com/a%2fb%41c"))
                    .isEqualTo("https://example.com/a%2FbAc");
        }

        @Test
        void rejectsUrlsWithoutSchemeOrHost() {
            assertThatThrownBy(() -> normalizers.normalize(IocType.URL, "example.com/path"))
                    .isInstanceOf(IocFormatException.class);
            assertThatThrownBy(() -> normalizers.normalize(IocType.URL, "https://///"))
                    .isInstanceOf(IocFormatException.class);
        }
    }

    @Nested
    class FileHash {

        @Test
        void lowercasesHexAndValidatesLength() {
            String upper = "2C26B46B68FFC68FF99B453C1D30413413422D706483BFA0F98A5E886266E7AE";
            assertThat(normalizers.normalize(IocType.FILE_HASH, upper))
                    .isEqualTo(upper.toLowerCase(java.util.Locale.ROOT));
            assertThat(normalizers.normalize(IocType.FILE_HASH, "d41d8cd98f00b204e9800998ecf8427e"))
                    .isEqualTo("d41d8cd98f00b204e9800998ecf8427e");
        }

        @Test
        void rejectsNonHexOrUnknownLength() {
            assertThatThrownBy(() -> normalizers.normalize(IocType.FILE_HASH, "zz-not-hex-zz"))
                    .isInstanceOf(IocFormatException.class);
            assertThatThrownBy(() -> normalizers.normalize(IocType.FILE_HASH, "abcdef"))
                    .isInstanceOf(IocFormatException.class);
        }
    }

    @Nested
    class Email {

        @Test
        void lowercasesDomainAndPreservesLocalPartCase() {
            assertThat(normalizers.normalize(IocType.EMAIL, "John.Doe@EXAMPLE.ORG"))
                    .isEqualTo("John.Doe@example.org");
        }

        @Test
        void rejectsMalformedEmails() {
            assertThatThrownBy(() -> normalizers.normalize(IocType.EMAIL, "not-an-email"))
                    .isInstanceOf(IocFormatException.class);
            assertThatThrownBy(() -> normalizers.normalize(IocType.EMAIL, "user@"))
                    .isInstanceOf(IocFormatException.class);
        }
    }

    @Nested
    class TypeInference {

        @Test
        void infersEachTypeAndReturnsNullForGarbage() {
            assertThat(normalizers.infer("https://example.com/x")).isEqualTo(IocType.URL);
            assertThat(normalizers.infer("user@example.com")).isEqualTo(IocType.EMAIL);
            assertThat(normalizers.infer("203.0.113.7")).isEqualTo(IocType.IPV4);
            assertThat(normalizers.infer("2001:db8::1")).isEqualTo(IocType.IPV6);
            assertThat(normalizers.infer("d41d8cd98f00b204e9800998ecf8427e")).isEqualTo(IocType.FILE_HASH);
            assertThat(normalizers.infer("evil.example.com")).isEqualTo(IocType.DOMAIN);
            assertThat(normalizers.infer("%%%not-an-ioc%%%")).isNull();
        }
    }

    @Nested
    class ReservedRanges {

        @Test
        void detectsAllSpecifiedIpv4Ranges() {
            for (String reserved : new String[] {
                "10.0.0.1",
                "172.16.0.1",
                "192.168.1.1",
                "127.0.0.1",
                "169.254.0.1",
                "100.64.0.1",
                "0.1.2.3",
                "224.0.0.1",
                "239.255.255.255"
            }) {
                assertThat(ReservedIpRanges.isReservedIpv4(reserved))
                        .as(reserved)
                        .isTrue();
            }
            assertThat(ReservedIpRanges.isReservedIpv4("203.0.113.7")).isFalse();
            assertThat(ReservedIpRanges.isReservedIpv4("100.128.0.1")).isFalse();
        }

        @Test
        void detectsSpecifiedIpv6Ranges() {
            assertThat(ReservedIpRanges.isReservedIpv6("::1")).isTrue();
            assertThat(ReservedIpRanges.isReservedIpv6("fc00::1")).isTrue();
            assertThat(ReservedIpRanges.isReservedIpv6("fdff::1")).isTrue();
            assertThat(ReservedIpRanges.isReservedIpv6("fe80::1")).isTrue();
            assertThat(ReservedIpRanges.isReservedIpv6("2001:db8::1")).isFalse();
        }
    }
}
