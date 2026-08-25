package com.ctip.application.ingestion;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 一個批次(INGESTION_BATCH_SIZE,一批一交易)內的共享狀態:
 * 批次內去重的 seen 集合與剩餘配額(null = 無配額;M2 手動提交帶入)。
 */
public final class BatchState {

    private final UUID sourceSyncId;
    private final Set<String> seenIdentities = new HashSet<>();
    private Integer remainingQuota;

    public BatchState(UUID sourceSyncId, Integer remainingQuota) {
        this.sourceSyncId = sourceSyncId;
        this.remainingQuota = remainingQuota;
    }

    public UUID sourceSyncId() {
        return sourceSyncId;
    }

    /** 第一次見到回 true;同批第二次起回 false(DUPLICATE_IN_BATCH)。 */
    boolean markSeen(String identityKey) {
        return seenIdentities.add(identityKey);
    }

    boolean quotaExhausted() {
        return remainingQuota != null && remainingQuota <= 0;
    }

    void consumeQuota() {
        if (remainingQuota != null) {
            remainingQuota--;
        }
    }
}
