package com.ctip.domain.indicator.normalization;

import com.ctip.sdk.IocType;
import java.util.Locale;

/**
 * EMAIL:domain 部分小寫(含 punycode);local part 保留原大小寫——
 * RFC 5321 規定 local part 大小寫敏感(docs/spec/07-domain-intel.md §7.2)。
 */
final class EmailNormalizer implements IocNormalizer {

    @Override
    public IocType type() {
        return IocType.EMAIL;
    }

    @Override
    public String normalize(String cleanedValue) {
        int at = cleanedValue.lastIndexOf('@');
        if (at <= 0 || at == cleanedValue.length() - 1) {
            throw new IocFormatException("email 必須是 local@domain:" + cleanedValue);
        }
        String local = cleanedValue.substring(0, at);
        if (local.chars().anyMatch(Character::isWhitespace)) {
            throw new IocFormatException("email local part 含空白");
        }
        String domain = DomainNormalizer.toAscii(cleanedValue.substring(at + 1)).toLowerCase(Locale.ROOT);
        DomainNormalizer.validate(domain);
        return local + "@" + domain;
    }
}
