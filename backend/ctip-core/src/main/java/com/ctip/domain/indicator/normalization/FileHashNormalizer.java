package com.ctip.domain.indicator.normalization;

import com.ctip.sdk.IocHashType;
import com.ctip.sdk.IocType;
import java.util.Locale;

/** FILE_HASH:小寫十六進位;依長度驗證 MD5=32 / SHA1=40 / SHA256=64 / SHA512=128(§7.2)。 */
final class FileHashNormalizer implements IocNormalizer {

    @Override
    public IocType type() {
        return IocType.FILE_HASH;
    }

    @Override
    public String normalize(String cleanedValue) {
        String value = cleanedValue.toLowerCase(Locale.ROOT);
        if (!value.chars().allMatch(c -> Character.digit(c, 16) >= 0)) {
            throw new IocFormatException("雜湊含非十六進位字元");
        }
        if (hashTypeOf(value) == null) {
            throw new IocFormatException("雜湊長度不屬於任何已知演算法:" + value.length());
        }
        return value;
    }

    /** 由長度推斷演算法;長度不合法回 null。 */
    static IocHashType hashTypeOf(String normalized) {
        return switch (normalized.length()) {
            case 32 -> IocHashType.MD5;
            case 40 -> IocHashType.SHA1;
            case 64 -> IocHashType.SHA256;
            case 128 -> IocHashType.SHA512;
            default -> null;
        };
    }
}
