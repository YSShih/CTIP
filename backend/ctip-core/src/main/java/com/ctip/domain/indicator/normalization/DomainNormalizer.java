package com.ctip.domain.indicator.normalization;

import com.ctip.sdk.IocType;
import java.net.IDN;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * DOMAIN:小寫、去尾端點、IDN 轉 punycode、`www.` 前綴去除可設定且預設不去除
 * (docs/spec/07-domain-intel.md §7.2)。JDK 的 {@link IDN} 實作 IDNA2003(RFC 3490);
 * 與規格點名的 IDNA2008 在極少數字元(如 ß、ZWJ)行為不同,版本表無 ICU4J 可用,
 * 差異已回報(見 ADR 0004)。
 */
final class DomainNormalizer implements IocNormalizer {

    static final int MAX_DOMAIN_LENGTH = 253;

    private static final Pattern LABEL = Pattern.compile("[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?");

    private final boolean stripWww;

    DomainNormalizer(boolean stripWww) {
        this.stripWww = stripWww;
    }

    @Override
    public IocType type() {
        return IocType.DOMAIN;
    }

    @Override
    public String normalize(String cleanedValue) {
        String value = cleanedValue.endsWith(".") ? cleanedValue.substring(0, cleanedValue.length() - 1) : cleanedValue;
        value = toAscii(value).toLowerCase(Locale.ROOT);
        if (stripWww && value.startsWith("www.") && value.indexOf('.', 4) > 0) {
            value = value.substring(4);
        }
        validate(value);
        return value;
    }

    static String toAscii(String value) {
        try {
            return IDN.toASCII(value.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IocFormatException("IDN 轉換失敗:" + value);
        }
    }

    static void validate(String domain) {
        if (domain.isEmpty() || domain.length() > MAX_DOMAIN_LENGTH) {
            throw new IocFormatException("網域長度不合法:" + domain);
        }
        String[] labels = domain.split("\\.", -1);
        if (labels.length < 2) {
            throw new IocFormatException("網域至少要有兩個 label:" + domain);
        }
        for (String label : labels) {
            if (!LABEL.matcher(label).matches()) {
                throw new IocFormatException("網域 label 不合法:" + domain);
            }
        }
    }
}
