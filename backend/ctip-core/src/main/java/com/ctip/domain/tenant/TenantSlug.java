package com.ctip.domain.tenant;

import java.util.regex.Pattern;

/** URL 安全識別碼,格式即不變量 T1 的正規表達式(docs/spec/02-ddd-model.md)。 */
public record TenantSlug(String value) {

    private static final Pattern FORMAT = Pattern.compile("^[a-z0-9][a-z0-9-]{0,62}[a-z0-9]$");

    public TenantSlug {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("slug 格式不符:" + value);
        }
    }
}
