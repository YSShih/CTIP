package com.ctip.infrastructure.observability;

import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

/**
 * 日誌的敏感欄位遮罩(docs/spec/13-platform-ops.md §13.6「絕不記錄」、14 §14.4 條 8)。
 *
 * <p>這是<strong>第二道防線</strong>:第一道是不把憑證交給 logger。但字串會經由例外訊息、
 * 請求本文回顯、第三方函式庫的 debug 輸出等路徑漏出來,那些路徑不在本專案的控制範圍內。
 *
 * <p>不遮罩 SHA-256 之類的十六進位摘要——指紋與 traceId 是查問題的主線索。
 * 判別方式是「同時含大小寫字母的 40 碼以上 base62 串」,十六進位摘要不符合。
 */
public final class SensitiveMasks {

    public static final String MASK = "***";

    /** refresh token 原文 48 碼、webhook 密鑰 40 碼,皆為 base62(§10.5、§13.2)。 */
    private static final int OPAQUE_TOKEN_MIN_LENGTH = 40;

    private static final Pattern OPAQUE_TOKEN = Pattern.compile("\\b[0-9A-Za-z]{" + OPAQUE_TOKEN_MIN_LENGTH + ",}\\b");

    private static final List<Rule> RULES = List.of(
            // JWT(access token 原文):三段 base64url,第一段一定以 eyJ 開頭
            new Rule(Pattern.compile("\\beyJ[A-Za-z0-9_=-]{4,}\\.[A-Za-z0-9_=-]{4,}\\.[A-Za-z0-9_=-]*"), MASK),
            // Authorization 標頭值(含非 Bearer 的 scheme)
            new Rule(Pattern.compile("(?i)\\bBearer\\s+\\S+"), "Bearer " + MASK),
            new Rule(Pattern.compile("(?i)(\"?authorization\"?\\s*[:=]\\s*\"?)[^\"\\s,}]+"), "$1" + MASK),
            // X-API-Key 標頭值
            new Rule(Pattern.compile("(?i)(\"?x-api-key\"?\\s*[:=]\\s*\"?)[^\"\\s,}]+"), "$1" + MASK),
            // API key 原文:保留環境前綴,隨機段遮掉(§10.5 的 ctip_<env>_<32 base62>)
            new Rule(Pattern.compile("\\bctip_(mvp|dev|stg|prod)_[0-9A-Za-z]{32}\\b"), "ctip_$1_" + MASK),
            // JSON / query 形式的密碼、密鑰、token 欄位
            new Rule(
                    Pattern.compile(
                            "(?i)(\"?[a-z]*(password|secret|token|apikey|api_key)\"?\\s*[:=]\\s*\"?)" + "[^\"\\s,}&]+"),
                    "$1" + MASK));

    private SensitiveMasks() {}

    public static String apply(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String masked = text;
        for (Rule rule : RULES) {
            masked = rule.pattern().matcher(masked).replaceAll(rule.replacement());
        }
        return OPAQUE_TOKEN.matcher(masked).replaceAll(SensitiveMasks::maskOpaqueToken);
    }

    private static String maskOpaqueToken(MatchResult match) {
        String candidate = match.group();
        return looksRandom(candidate) ? MASK : candidate;
    }

    /** base62 隨機串必含大小寫兩種字母;十六進位摘要、UUID、純數字都不會。 */
    private static boolean looksRandom(String candidate) {
        boolean upper = false;
        boolean lower = false;
        for (int i = 0; i < candidate.length(); i++) {
            char c = candidate.charAt(i);
            upper = upper || Character.isUpperCase(c);
            lower = lower || Character.isLowerCase(c);
        }
        return upper && lower;
    }

    private record Rule(Pattern pattern, String replacement) {}
}
