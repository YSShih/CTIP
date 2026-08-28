package com.ctip.infrastructure.persistence;

import com.ctip.application.ingestion.RejectedRecord;
import com.ctip.application.port.IdGeneratorPort;
import com.ctip.application.port.RejectionLogPort;
import org.springframework.stereotype.Repository;

/**
 * RejectionLogPort 的 JPA 實作:ingestion_rejections 為 append-only 兩模型表。
 * 交易由呼叫端(IngestionBatchProcessor 的批次交易)持有,拒絕列與該批一起提交。
 */
@Repository
class RejectionLogAdapter implements RejectionLogPort {

    /** raw_value 欄位上限(docs/spec/04-data-dictionary.md 表 7);超長來源值防禦性截斷。 */
    private static final int MAX_RAW_VALUE = 4096;

    private final IngestionRejectionJpaRepository jpa;
    private final IdGeneratorPort idGenerator;

    RejectionLogAdapter(IngestionRejectionJpaRepository jpa, IdGeneratorPort idGenerator) {
        this.jpa = jpa;
        this.idGenerator = idGenerator;
    }

    @Override
    public void record(RejectedRecord rejected) {
        IngestionRejectionEntity entity = new IngestionRejectionEntity();
        entity.id = idGenerator.nextId();
        entity.sourceId = rejected.sourceId().value();
        entity.sourceSyncId = rejected.sourceSyncId();
        entity.importJobId = rejected.importJobId();
        entity.rawValue = truncate(rejected.rawValue());
        entity.declaredType =
                rejected.declaredType() == null ? null : rejected.declaredType().name();
        entity.reason = rejected.reason().name();
        entity.detail = rejected.detail();
        jpa.save(entity);
    }

    private static String truncate(String rawValue) {
        if (rawValue == null) {
            return "";
        }
        return rawValue.length() <= MAX_RAW_VALUE ? rawValue : rawValue.substring(0, MAX_RAW_VALUE);
    }
}
