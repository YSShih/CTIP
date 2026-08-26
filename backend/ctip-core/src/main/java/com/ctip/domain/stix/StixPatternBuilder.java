package com.ctip.domain.stix;

import com.ctip.domain.indicator.IocValue;
import com.ctip.sdk.IocHashType;

/**
 * 六種 IocType 的固定 pattern 模板(docs/spec/07-domain-intel.md §7.8.3)。
 * 值一律取 normalizedValue 並經 {@link StixPatternEscaper} 跳脫;
 * FILE_HASH 的雜湊鍵必須經 hashing-algorithm-ov 對應(SHA256 → SHA-256),不得直接用 enum 名。
 */
public final class StixPatternBuilder {

    private StixPatternBuilder() {}

    public static String pattern(IocValue value) {
        String v = StixPatternEscaper.escape(value.normalized());
        return switch (value.type()) {
            case IPV4 -> "[ipv4-addr:value = '" + v + "']";
            case IPV6 -> "[ipv6-addr:value = '" + v + "']";
            case DOMAIN -> "[domain-name:value = '" + v + "']";
            case URL -> "[url:value = '" + v + "']";
            case EMAIL -> "[email-addr:value = '" + v + "']";
            case FILE_HASH -> "[file:hashes.'" + hashKey(value.hashType()) + "' = '" + v + "']";
        };
    }

    /** IocHashType → STIX hashing-algorithm-ov(注意連字號;§7.8.3 對應表)。 */
    public static String hashKey(IocHashType hashType) {
        return switch (hashType) {
            case MD5 -> "MD5";
            case SHA1 -> "SHA-1";
            case SHA256 -> "SHA-256";
            case SHA512 -> "SHA-512";
        };
    }
}
