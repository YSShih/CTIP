package com.ctip.application.ingestion;

import com.ctip.application.bloom.BloomChangeTracker;
import com.ctip.domain.bloom.BloomMembership;
import com.ctip.domain.bloom.BloomScope;
import com.ctip.domain.indicator.Indicator;
import com.ctip.domain.tenant.TenantId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stage 10 BloomUpdate(PersistStage 之後):標記本批影響到哪些 Bloom scope。
 *
 * <p><strong>這裡不維護成員集合</strong>——成員的真相來源是資料庫,生成時以水位查詢取得。
 * 記憶體累積若因重啟遺失會產生 Bloom false negative(client 以為安全),而
 * 「未命中不代表安全」正是 §11.1 最強調的語意,不該再由實作自己製造出更多假陰性。
 *
 * <p>本 stage 的用途是<strong>跳過沒有變動的 scope</strong>:每小時產生空 delta 會白白消耗
 * §11.3 的 24 段 chain 預算,逼 client 無謂地重下 full snapshot。
 *
 * <p>比照 {@code StixProjectionStage}:例外只記錄,絕不使該筆 IOC 被拒絕——
 * Bloom 是衍生資料,它的失敗不該讓攝取失敗。
 */
public final class BloomUpdateStage implements IngestionStage {

    private static final Logger log = LoggerFactory.getLogger(BloomUpdateStage.class);

    private final BloomChangeTracker changes;

    public BloomUpdateStage(BloomChangeTracker changes) {
        this.changes = changes;
    }

    @Override
    public String name() {
        return "BloomUpdate";
    }

    @Override
    public IngestionContext execute(IngestionContext context) {
        Indicator indicator = context.indicator();
        if (indicator == null) {
            return context;
        }
        try {
            TenantId owner = indicator.ownerTenantId();
            if (BloomMembership.inPublicBloom(indicator)) {
                changes.markChanged(BloomScope.PUBLIC, TenantId.PUBLIC);
            } else if (BloomMembership.inTenantBloom(indicator, owner)) {
                changes.markChanged(BloomScope.TENANT, owner);
            }
        } catch (RuntimeException e) {
            log.warn("Bloom 變動標記失敗,indicator={}", indicator.id().value(), e);
        }
        return context;
    }
}
