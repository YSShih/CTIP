package com.ctip.infrastructure.persistence;

import com.ctip.domain.fingerprint.Fingerprint;
import com.ctip.domain.indicator.HashRecord;
import com.ctip.domain.indicator.Indicator;
import com.ctip.domain.indicator.IndicatorId;
import com.ctip.domain.indicator.IndicatorSnapshot;
import com.ctip.domain.indicator.IndicatorSourceSnapshot;
import com.ctip.domain.indicator.IndicatorStatus;
import com.ctip.domain.indicator.IocValue;
import com.ctip.domain.indicator.SourceRecordStatus;
import com.ctip.domain.source.SourceId;
import com.ctip.domain.tenant.TenantId;
import com.ctip.sdk.Confidence;
import com.ctip.sdk.FingerprintAlgorithm;
import com.ctip.sdk.IocHashType;
import com.ctip.sdk.IocType;
import com.ctip.sdk.RedistributionPolicy;
import com.ctip.sdk.Severity;
import com.ctip.sdk.Tlp;
import java.util.Set;
import java.util.UUID;
import org.mapstruct.Mapper;

/** Indicator 聚合 ↔ JPA entity graph。tags 僅物化聯集於 indicators.tags(I9)。 */
@Mapper(componentModel = "spring")
interface IndicatorMapper {

    default Indicator toDomain(IndicatorEntity e) {
        return Indicator.reconstitute(new IndicatorSnapshot(
                new IndicatorId(e.id),
                new TenantId(e.ownerTenantId),
                new IocValue(
                        IocType.valueOf(e.type),
                        e.hashType == null ? null : IocHashType.valueOf(e.hashType),
                        e.value,
                        e.normalizedValue),
                new Fingerprint(e.fingerprint.trim()),
                e.firstSeen,
                e.lastSeen,
                e.validUntil,
                Confidence.of(e.confidence),
                Severity.valueOf(e.severity),
                e.score,
                Tlp.valueOf(e.tlp),
                IndicatorStatus.valueOf(e.status),
                Set.of(e.tags),
                e.sources.stream().map(this::toSourceSnapshot).toList(),
                e.hashRecords.stream().map(this::toHashRecord).toList()));
    }

    default IndicatorSourceSnapshot toSourceSnapshot(IndicatorSourceEntity r) {
        return new IndicatorSourceSnapshot(
                new SourceId(r.sourceId),
                r.sourceValue,
                r.sourceConfidence == null ? null : Confidence.of(r.sourceConfidence),
                r.sourceSeverity == null ? null : Severity.valueOf(r.sourceSeverity),
                Tlp.valueOf(r.sourceTlp),
                r.sourceFirstSeen,
                r.sourceLastSeen,
                r.sourceValidUntil,
                RedistributionPolicy.valueOf(r.redistributionPolicy),
                r.reportCount,
                SourceRecordStatus.valueOf(r.status),
                Set.of());
    }

    default HashRecord toHashRecord(HashRecordEntity h) {
        return new HashRecord(
                FingerprintAlgorithm.valueOf(h.algorithm),
                h.digest,
                h.sourceId == null ? null : new SourceId(h.sourceId));
    }

    default void updateEntity(IndicatorSnapshot s, IndicatorEntity e) {
        e.id = s.id().value();
        e.ownerTenantId = s.ownerTenantId().value();
        e.type = s.value().type().name();
        e.hashType = s.value().hashType() == null ? null : s.value().hashType().name();
        e.value = s.value().raw();
        e.normalizedValue = s.value().normalized();
        e.fingerprint = s.fingerprint().hex();
        e.firstSeen = s.firstSeen();
        e.lastSeen = s.lastSeen();
        e.validFrom = s.firstSeen();
        e.validUntil = s.validUntil();
        e.confidence = (short) s.confidence().value();
        e.severity = s.severity().name();
        e.score = (short) s.score();
        e.tlp = s.tlp().name();
        e.status = s.status().name();
        e.tags = s.tags().stream().sorted().toArray(String[]::new);
        e.sourceCount = (short) s.sources().stream()
                .filter(r -> r.status() == SourceRecordStatus.ACTIVE)
                .count();
        reconcileSources(s, e);
        reconcileHashRecords(s, e);
    }

    private void reconcileSources(IndicatorSnapshot s, IndicatorEntity e) {
        e.sources.removeIf(existing ->
                s.sources().stream().noneMatch(r -> r.sourceId().value().equals(existing.sourceId)));
        for (IndicatorSourceSnapshot r : s.sources()) {
            IndicatorSourceEntity target = e.sources.stream()
                    .filter(existing -> existing.sourceId.equals(r.sourceId().value()))
                    .findFirst()
                    .orElseGet(() -> {
                        IndicatorSourceEntity created = new IndicatorSourceEntity();
                        created.id = UUID.randomUUID();
                        created.indicator = e;
                        created.sourceId = r.sourceId().value();
                        e.sources.add(created);
                        return created;
                    });
            target.sourceValue = r.sourceValue();
            target.sourceConfidence = r.sourceConfidence() == null
                    ? null
                    : (short) r.sourceConfidence().value();
            target.sourceSeverity =
                    r.sourceSeverity() == null ? null : r.sourceSeverity().name();
            target.sourceTlp = r.sourceTlp().name();
            target.sourceFirstSeen = r.sourceFirstSeen();
            target.sourceLastSeen = r.sourceLastSeen();
            target.sourceValidUntil = r.sourceValidUntil();
            target.redistributionPolicy = r.redistributionPolicy().name();
            target.reportCount = r.reportCount();
            target.status = r.status().name();
        }
    }

    private void reconcileHashRecords(IndicatorSnapshot s, IndicatorEntity e) {
        e.hashRecords.removeIf(existing -> s.hashRecords().stream()
                .noneMatch(h -> h.algorithm().name().equals(existing.algorithm)
                        && h.digest().equals(existing.digest)));
        for (HashRecord h : s.hashRecords()) {
            boolean exists = e.hashRecords.stream()
                    .anyMatch(existing ->
                            existing.algorithm.equals(h.algorithm().name()) && existing.digest.equals(h.digest()));
            if (!exists) {
                HashRecordEntity created = new HashRecordEntity();
                created.id = UUID.randomUUID();
                created.indicator = e;
                created.algorithm = h.algorithm().name();
                created.digest = h.digest();
                created.sourceId = h.sourceId() == null ? null : h.sourceId().value();
                e.hashRecords.add(created);
            }
        }
    }
}
