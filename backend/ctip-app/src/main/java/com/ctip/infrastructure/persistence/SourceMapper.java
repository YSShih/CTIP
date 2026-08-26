package com.ctip.infrastructure.persistence;

import com.ctip.domain.source.Reputation;
import com.ctip.domain.source.Source;
import com.ctip.domain.source.SourceHealth;
import com.ctip.domain.source.SourceId;
import com.ctip.domain.source.SourceSnapshot;
import com.ctip.domain.source.SourceStatus;
import com.ctip.sdk.RedistributionPolicy;
import com.ctip.sdk.SourceType;
import com.ctip.sdk.Tlp;
import java.time.Duration;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

/** Source domain ↔ JPA entity。 */
@Mapper(componentModel = "spring")
interface SourceMapper {

    default Source toDomain(SourceEntity e) {
        SourceHealth health = new SourceHealth(
                SourceStatus.valueOf(e.status),
                e.consecutiveFailures,
                e.lastSyncAt,
                e.lastSuccessAt,
                e.lastFailureAt,
                e.avgLatencyMs);
        return Source.reconstitute(new SourceSnapshot(
                new SourceId(e.id),
                SourceType.valueOf(e.sourceType),
                e.displayName,
                e.homepageUrl,
                Tlp.valueOf(e.defaultTlp),
                RedistributionPolicy.valueOf(e.redistributionPolicy),
                new Reputation(e.reputation),
                e.enabled,
                e.syncable,
                e.recommendedIntervalSeconds == null ? null : Duration.ofSeconds(e.recommendedIntervalSeconds),
                health,
                e.lastErrorMessage,
                e.nextCursor,
                e.totalRecordsIngested));
    }

    /** 只回寫聚合會變動的欄位;身分欄位(source_type、顯示資訊)由種子/管理流程持有。 */
    default void updateEntity(Source source, @MappingTarget SourceEntity e) {
        SourceSnapshot s = source.snapshot();
        e.reputation = (short) s.reputation().value();
        e.enabled = s.enabled();
        e.status = s.health().status().name();
        e.consecutiveFailures = s.health().consecutiveFailures();
        e.lastSyncAt = s.health().lastSyncAt();
        e.lastSuccessAt = s.health().lastSuccessAt();
        e.lastFailureAt = s.health().lastFailureAt();
        e.avgLatencyMs = s.health().avgLatencyMs();
        e.lastErrorMessage = s.lastErrorMessage();
        e.nextCursor = s.nextCursor();
        e.totalRecordsIngested = s.totalRecordsIngested();
    }
}
