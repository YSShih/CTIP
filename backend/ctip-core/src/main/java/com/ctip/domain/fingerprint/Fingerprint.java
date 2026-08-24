package com.ctip.domain.fingerprint;

import java.util.regex.Pattern;

/** 去重指紋:SHA-256(normalizedValue) 的 64 字元小寫十六進位。不是識別鍵(docs/spec/07-domain-intel.md §7.4)。 */
public record Fingerprint(String hex) {

    private static final Pattern FORMAT = Pattern.compile("^[0-9a-f]{64}$");

    public Fingerprint {
        if (hex == null || !FORMAT.matcher(hex).matches()) {
            throw new IllegalArgumentException("fingerprint 必須為 64 字元小寫十六進位");
        }
    }
}
