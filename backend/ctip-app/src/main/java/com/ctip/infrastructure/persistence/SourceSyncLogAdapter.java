package com.ctip.infrastructure.persistence;

import com.ctip.application.port.IdGeneratorPort;
import com.ctip.application.port.SourceSyncLogPort;
import com.ctip.application.source.SourceSyncReport;
import com.ctip.application.source.SyncResult;
import com.ctip.domain.source.SourceId;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * SourceSyncLogPort 的 JPA 實作(source_sync,兩模型表):start 落 RUNNING 列、
 * finish 回寫一次結果。各自獨立交易(REQUIRES_NEW)——RUNNING 列必須在 fetch 開始前
 * 就可見,失敗回報也不得被外層 rollback 帶走。
 */
@Repository
class SourceSyncLogAdapter implements SourceSyncLogPort {

    private final SourceSyncJpaRepository jpa;
    private final IdGeneratorPort idGenerator;

    SourceSyncLogAdapter(SourceSyncJpaRepository jpa, IdGeneratorPort idGenerator) {
        this.jpa = jpa;
        this.idGenerator = idGenerator;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID start(SourceId sourceId, Instant startedAt) {
        SourceSyncEntity entity = new SourceSyncEntity();
        entity.id = idGenerator.nextId();
        entity.sourceId = sourceId.value();
        entity.startedAt = startedAt;
        entity.result = SyncResult.RUNNING.name();
        jpa.save(entity);
        return entity.id;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finish(SourceSyncReport report) {
        SourceSyncEntity entity = jpa.findById(report.sourceSyncId())
                .orElseThrow(() -> new IllegalStateException("source_sync 列不存在:" + report.sourceSyncId()));
        entity.result = report.result().name();
        entity.finishedAt = report.finishedAt();
        entity.durationMs = (int) Math.min(
                Integer.MAX_VALUE,
                Duration.between(entity.startedAt, report.finishedAt()).toMillis());
        entity.recordsFetched = report.recordsFetched();
        entity.recordsAccepted = report.recordsAccepted();
        entity.recordsRejected = report.recordsRejected();
        entity.recordsMerged = report.recordsMerged();
        entity.errorMessage = report.errorMessage();
        jpa.save(entity);
    }
}
