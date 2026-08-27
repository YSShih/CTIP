package com.ctip.domain.identity;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 完整 key 的格式:{@code ctip_<env>_<32 random base62>}(docs/spec/10-identity-plans.md §10.5)。
 * 隨機段由 application 層以 SecureRandom 產生(domain 不得取亂數,ArchUnit 規則 9)。
 */
public final class ApiKeyFormat {

    /** §10.5:env ∈ {mvp, dev, stg, prod}。 */
    private static final Pattern FULL_KEY = Pattern.compile("^ctip_(mvp|dev|stg|prod)_([0-9A-Za-z]{32})$");

    public static final int RANDOM_SEGMENT_LENGTH = 32;

    private ApiKeyFormat() {}

    public static String compose(String environment, String randomSegment) {
        String fullKey = "ctip_" + environment + "_" + randomSegment;
        requireValid(fullKey);
        return fullKey;
    }

    /** 不變量 K2:前綴取自隨機段前 8 碼(整串前 8 碼是環境常數,無法唯一,見 ADR 0012 決策 2)。 */
    public static KeyPrefix prefixOf(String fullKey) {
        return new KeyPrefix(randomSegmentOf(fullKey).substring(0, KeyPrefix.LENGTH));
    }

    public static String randomSegmentOf(String fullKey) {
        return requireValid(fullKey).group(2);
    }

    public static boolean isWellFormed(String candidate) {
        return candidate != null && FULL_KEY.matcher(candidate).matches();
    }

    private static Matcher requireValid(String fullKey) {
        if (fullKey == null) {
            throw new IllegalArgumentException("API key 不得為 null");
        }
        Matcher matcher = FULL_KEY.matcher(fullKey);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("API key 格式不符 ctip_<env>_<32 base62>");
        }
        return matcher;
    }
}
