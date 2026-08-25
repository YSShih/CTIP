package com.ctip.application.ingestion;

import com.ctip.domain.indicator.IocValue;
import com.ctip.domain.indicator.normalization.IocFormatException;
import com.ctip.domain.indicator.normalization.IocNormalizers;
import com.ctip.domain.indicator.normalization.ReservedIpRanges;
import com.ctip.sdk.IocType;
import java.util.Set;

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
        this.domainAllowlist = Set.copyOf(domainAllowlist);
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
