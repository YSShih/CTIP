package com.ctip.application.ingestion;

import com.ctip.domain.indicator.Indicator;
import com.ctip.domain.stix.StixProjection;
import java.util.List;

/**
 * 單筆記錄流經 pipeline 的結果(手動提交端點需要它:§9.7 要回完整 Indicator DTO,
 * 合併至既有 IOC 回 200、新建回 201;被 pipeline 拒絕則要映射成錯誤而非假成功)。
 * 批次路徑用 {@link BatchOutcome} 的計數即可,不逐筆保留——一次同步可達百萬筆。
 */
public record RecordOutcome(
        Indicator indicator,
        boolean merged,
        List<StixProjection> projections,
        RejectionReason rejectionReason,
        String rejectionDetail) {

    public RecordOutcome {
        projections = projections == null ? List.of() : List.copyOf(projections);
    }

    public boolean rejected() {
        return rejectionReason != null;
    }
}
