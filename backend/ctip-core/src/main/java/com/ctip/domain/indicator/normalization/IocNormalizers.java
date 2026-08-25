package com.ctip.domain.indicator.normalization;

import com.ctip.sdk.IocType;
import java.util.EnumMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 正規化入口(docs/spec/07-domain-intel.md §7.2):共通清理 + 依 IocType 分派 + 型別推斷。
 * 指紋一律針對 normalize 的輸出計算,絕不針對原始值。
 */
public final class IocNormalizers {

    private static final Pattern ZERO_WIDTH = Pattern.compile("[\\u200B\\u200C\\u200D\\uFEFF]");
    private static final Pattern IPV4_SHAPE = Pattern.compile("[0-9]{1,3}(\\.[0-9]{1,3}){3}");
    private static final Pattern IPV6_SHAPE = Pattern.compile("[0-9a-fA-F:]*:[0-9a-fA-F:.]*");
    private static final Pattern HASH_SHAPE =
            Pattern.compile("[0-9a-fA-F]{32}|[0-9a-fA-F]{40}|[0-9a-fA-F]{64}|[0-9a-fA-F]{128}");
    private static final Pattern DOMAIN_SHAPE = Pattern.compile("[\\p{L}\\p{N}_-]+(\\.[\\p{L}\\p{N}_-]+)+\\.?");

    private final Map<IocType, IocNormalizer> byType = new EnumMap<>(IocType.class);

    public IocNormalizers(boolean stripWww) {
        register(new Ipv4Normalizer());
        register(new Ipv6Normalizer());
        register(new DomainNormalizer(stripWww));
        register(new UrlNormalizer());
        register(new FileHashNormalizer());
        register(new EmailNormalizer());
    }

    private void register(IocNormalizer normalizer) {
        byType.put(normalizer.type(), normalizer);
    }

    /** 共通清理:去前後空白、移除零寬字元(U+200B–U+200D、U+FEFF)與控制字元。 */
    public String clean(String rawValue) {
        StringBuilder sb = new StringBuilder(rawValue.length());
        ZERO_WIDTH.matcher(rawValue).replaceAll("").chars().forEach(c -> {
            if (!Character.isISOControl(c)) {
                sb.appendCodePoint(c);
            }
        });
        return sb.toString().strip();
    }

    /** 依型別正規化;格式不合丟 {@link IocFormatException}。 */
    public String normalize(IocType type, String cleanedValue) {
        return byType.get(type).normalize(cleanedValue);
    }

    /** 由正規化後的雜湊長度推斷演算法(來源未宣告 IocHashType 時);長度必然合法(normalize 已驗證)。 */
    public com.ctip.sdk.IocHashType inferHashType(String normalizedHash) {
        return FileHashNormalizer.hashTypeOf(normalizedHash);
    }

    /** 來源未宣告型別時的推斷;無法推斷回傳 null(拒絕規則 UNKNOWN_TYPE)。 */
    public IocType infer(String cleanedValue) {
        if (cleanedValue.contains("://")) {
            return IocType.URL;
        }
        if (cleanedValue.indexOf('@') > 0 && !cleanedValue.contains("/")) {
            return IocType.EMAIL;
        }
        if (IPV4_SHAPE.matcher(cleanedValue).matches()) {
            return IocType.IPV4;
        }
        if (IPV6_SHAPE.matcher(cleanedValue).matches()) {
            return IocType.IPV6;
        }
        if (HASH_SHAPE.matcher(cleanedValue).matches()) {
            return IocType.FILE_HASH;
        }
        if (DOMAIN_SHAPE.matcher(cleanedValue).matches()) {
            return IocType.DOMAIN;
        }
        return null;
    }
}
