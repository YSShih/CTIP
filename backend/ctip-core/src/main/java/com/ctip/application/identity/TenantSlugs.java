package com.ctip.application.identity;

import com.ctip.domain.tenant.TenantSlug;
import java.util.Locale;
import java.util.UUID;

/** 由使用者輸入導出合法 slug(不變量 T1 的格式面);碰撞時以 UUID 前 8 碼加尾綴。 */
public final class TenantSlugs {

    private static final int MAX_BASE_LENGTH = 40;
    private static final int SUFFIX_LENGTH = 8;

    private TenantSlugs() {}

    public static String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        String cleaned = raw.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");
        return cleaned.length() > MAX_BASE_LENGTH ? trimTrailingHyphen(cleaned.substring(0, MAX_BASE_LENGTH)) : cleaned;
    }

    public static TenantSlug withSuffix(String base, UUID uniqueness) {
        String suffix = uniqueness.toString().replace("-", "").substring(0, SUFFIX_LENGTH);
        String prefix = base.isEmpty() ? "t" : base;
        return new TenantSlug(prefix + "-" + suffix);
    }

    private static String trimTrailingHyphen(String value) {
        return value.replaceAll("-+$", "");
    }
}
