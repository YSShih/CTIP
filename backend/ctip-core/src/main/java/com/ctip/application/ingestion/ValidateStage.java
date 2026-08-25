package com.ctip.application.ingestion;

import com.ctip.sdk.IocHashType;
import com.ctip.sdk.IocType;

/**
 * Stage 2 Validate(拒絕規則 §7.3 的前置檢查):配額、長度上限、宣告雜湊長度。
 * 需要正規化結果的規則(私有 IP、allowlist、格式驗證)在 NormalizeStage 緊接執行(ADR 0004)。
 */
public final class ValidateStage implements IngestionStage {

    static final int MAX_URL_LENGTH = 2048;
    static final int MAX_DOMAIN_LENGTH = 253;
    static final int MAX_EMAIL_LENGTH = 320;

    @Override
    public String name() {
        return "Validate";
    }

    @Override
    public IngestionContext execute(IngestionContext context) {
        if (context.batch().quotaExhausted()) {
            context.reject(RejectionReason.QUOTA_EXCEEDED, "超出方案配額");
            return context;
        }
        int length = context.cleanedValue().length();
        Integer max = maxLength(context.type());
        if (max != null && length > max) {
            context.reject(RejectionReason.LENGTH_EXCEEDED, context.type() + " 長度 " + length + " 超過上限 " + max);
            return context;
        }
        if (context.type() == IocType.FILE_HASH && context.hashType() != null) {
            int expected = expectedHexLength(context.hashType());
            if (length != expected) {
                context.reject(
                        RejectionReason.HASH_LENGTH_MISMATCH,
                        "宣告 " + context.hashType() + " 應為 " + expected + " 字元,實得 " + length);
            }
        }
        return context;
    }

    private static Integer maxLength(IocType type) {
        return switch (type) {
            case URL -> MAX_URL_LENGTH;
            case DOMAIN -> MAX_DOMAIN_LENGTH;
            case EMAIL -> MAX_EMAIL_LENGTH;
            case IPV4, IPV6, FILE_HASH -> null;
        };
    }

    private static int expectedHexLength(IocHashType hashType) {
        return switch (hashType) {
            case MD5 -> 32;
            case SHA1 -> 40;
            case SHA256 -> 64;
            case SHA512 -> 128;
        };
    }
}
