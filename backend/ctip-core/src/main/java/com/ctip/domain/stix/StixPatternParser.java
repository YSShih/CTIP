package com.ctip.domain.stix;

import com.ctip.sdk.IocHashType;
import com.ctip.sdk.IocType;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@link StixPatternBuilder} 的反向:從 STIX pattern 取出型別與值(匯入 STIX bundle 用,§9.7)。
 *
 * <p>只認得 §7.8.3 的六個固定模板——那是本平台自己輸出的形狀,因此匯出的 bundle 一定能再匯入。
 * 完整的 STIX pattern 語法(布林組合、比較運算子、時間窗)刻意<strong>不支援</strong>:
 * 認不得的 pattern 回 empty,由呼叫端逐筆記為 {@code UNKNOWN_TYPE} 拒絕
 * ——猜測式的解析會把「看起來像 IOC 的字串」當成情資寫進資料庫。
 */
public final class StixPatternParser {

    /**
     * 字串常值:非引號非反斜線的字元,或一組跳脫序列。
     *
     * <p>刻意<strong>不用</strong> {@code (.*)}:那會讓
     * {@code [ipv4-addr:value = 'a'] AND [url:value = 'b']} 這種複合 pattern 被貪婪地當成
     * 單一值 {@code a'] AND [url:value = 'b},於是一段本該被拒絕的 pattern 變成一筆假 IOC。
     */
    private static final String LITERAL = "((?:[^'\\\\]|\\\\.)*)";

    private static final Pattern SIMPLE = Pattern.compile("^\\[\\s*([a-z0-9-]+):value\\s*=\\s*'" + LITERAL + "'\\s*]$");

    private static final Pattern FILE_HASH =
            Pattern.compile("^\\[\\s*file:hashes\\.'([A-Za-z0-9-]+)'\\s*=\\s*'" + LITERAL + "'\\s*]$");

    private StixPatternParser() {}

    /** 解析結果;{@code hashType} 僅 FILE_HASH 非 null。值已還原跳脫。 */
    public record ParsedPattern(IocType type, IocHashType hashType, String value) {}

    public static Optional<ParsedPattern> parse(String pattern) {
        if (pattern == null) {
            return Optional.empty();
        }
        String trimmed = pattern.trim();
        Matcher hash = FILE_HASH.matcher(trimmed);
        if (hash.matches()) {
            return hashType(hash.group(1))
                    .map(type -> new ParsedPattern(IocType.FILE_HASH, type, unescape(hash.group(2))));
        }
        Matcher simple = SIMPLE.matcher(trimmed);
        if (!simple.matches()) {
            return Optional.empty();
        }
        return iocType(simple.group(1)).map(type -> new ParsedPattern(type, null, unescape(simple.group(2))));
    }

    private static Optional<IocType> iocType(String stixObject) {
        return switch (stixObject) {
            case "ipv4-addr" -> Optional.of(IocType.IPV4);
            case "ipv6-addr" -> Optional.of(IocType.IPV6);
            case "domain-name" -> Optional.of(IocType.DOMAIN);
            case "url" -> Optional.of(IocType.URL);
            case "email-addr" -> Optional.of(IocType.EMAIL);
            default -> Optional.empty();
        };
    }

    /** hashing-algorithm-ov → IocHashType(連字號;§7.8.3 對應表的反向)。 */
    private static Optional<IocHashType> hashType(String algorithm) {
        return switch (algorithm.toUpperCase(Locale.ROOT)) {
            case "MD5" -> Optional.of(IocHashType.MD5);
            case "SHA-1", "SHA1" -> Optional.of(IocHashType.SHA1);
            case "SHA-256", "SHA256" -> Optional.of(IocHashType.SHA256);
            case "SHA-512", "SHA512" -> Optional.of(IocHashType.SHA512);
            default -> Optional.empty();
        };
    }

    /** {@link StixPatternEscaper} 的反向:{@code \\'} → {@code '}、{@code \\\\} → {@code \\}。 */
    private static String unescape(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' && i + 1 < value.length()) {
                out.append(value.charAt(++i));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
