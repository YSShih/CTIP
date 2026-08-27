package com.ctip.application.ingestion;

import com.ctip.domain.indicator.IocValue;
import com.ctip.domain.indicator.normalization.IocFormatException;
import com.ctip.domain.indicator.normalization.IocNormalizers;
import com.ctip.domain.indicator.normalization.ReservedIpRanges;
import com.ctip.sdk.IocType;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Stage 3 Normalize(正規化規則 §7.2)+ 需要 canonical 值的拒絕規則(§7.3):
 * 私有/保留 IP(除非來源明示允許)、良性網域 allowlist(僅 DOMAIN、exact match,
 * URL 不套用、不得後綴比對)。預設 allowlist 為空(ctip.data-quality.domain-allowlist)。
 */
public final class NormalizeStage implements IngestionStage {

    private final IocNormalizers normalizers;
    private final Set<String> domainAllowlist;

    public NormalizeStage(IocNormalizers normalizers, Set<String> domainAllowlist) {
        this.normalizers = normalizers;
        // allowlist 項目套用與 feed 值相同的正規化,否則大小寫/IDN 寫法差異會使比對靜默失效
        this.domainAllowlist = domainAllowlist.stream()
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .map(entry -> normalizeAllowlistEntry(normalizers, entry))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String normalizeAllowlistEntry(IocNormalizers normalizers, String entry) {
        try {
            return normalizers.normalize(IocType.DOMAIN, entry);
        } catch (IocFormatException e) {
            return entry.toLowerCase(Locale.ROOT);
        }
    }

    @Override
    public String name() {
        return "Normalize";
    }

    @Override
    public IngestionContext execute(IngestionContext context) {
        String normalized;
        try {
            normalized = normalizers.normalize(context.type(), context.cleanedValue());
        } catch (IocFormatException e) {
            context.reject(RejectionReason.MALFORMED_VALUE, e.getMessage());
            return context;
        }
        if (normalized.length() > ValidateStage.MAX_STORED_VALUE_LENGTH) {
            // punycode/百分比編碼可能使 normalized 長於 cleaned;超出 DB 欄位上限會在 flush 期炸掉整批交易
            context.reject(
                    RejectionReason.LENGTH_EXCEEDED,
                    "正規化後長度 " + normalized.length() + " 超過儲存上限 " + ValidateStage.MAX_STORED_VALUE_LENGTH);
            return context;
        }
        if (isReservedIp(context.type(), normalized) && !context.source().allowsPrivateIps()) {
            context.reject(RejectionReason.PRIVATE_OR_RESERVED_IP, normalized);
            return context;
        }
        if (context.type() == IocType.DOMAIN && domainAllowlist.contains(normalized)) {
            context.reject(RejectionReason.ALLOWLISTED_DOMAIN, normalized);
            return context;
        }
        if (context.type() == IocType.FILE_HASH && context.hashType() == null) {
            context.hashType(normalizers.inferHashType(normalized));
        }
        context.normalizedValue(normalized);
        context.iocValue(new IocValue(
                context.type(),
                context.type() == IocType.FILE_HASH ? context.hashType() : null,
                context.raw().rawValue(),
                normalized));
        return context;
    }

    private static boolean isReservedIp(IocType type, String normalized) {
        return switch (type) {
            case IPV4 -> ReservedIpRanges.isReservedIpv4(normalized);
            case IPV6 -> ReservedIpRanges.isReservedIpv6(normalized);
            default -> false;
        };
    }
}
