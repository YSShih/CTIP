package com.ctip.domain.indicator;

import com.ctip.domain.fingerprint.Fingerprint;
import com.ctip.domain.tenant.TenantId;
import com.ctip.sdk.Confidence;
import com.ctip.sdk.Severity;
import com.ctip.sdk.Tlp;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/** Indicator 聚合的持久化快照(重建與寫出用)。 */
public record IndicatorSnapshot(
        IndicatorId id,
        TenantId ownerTenantId,
        IocValue value,
        Fingerprint fingerprint,
        Instant firstSeen,
        Instant lastSeen,
        Instant validUntil,
        Confidence confidence,
        Severity severity,
        int score,
        Tlp tlp,
        IndicatorStatus status,
        Set<String> tags,
        List<IndicatorSourceSnapshot> sources,
        List<HashRecord> hashRecords) {}
