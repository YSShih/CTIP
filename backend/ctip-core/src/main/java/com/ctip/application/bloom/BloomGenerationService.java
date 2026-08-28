package com.ctip.application.bloom;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 排程的單一入口:決定每一輪要對哪些 scope 做什麼(docs/spec/11-sync-bloom.md §11.3 的快照政策)。
 *
 * <p>排程類別不含業務規則(與 {@code IngestionSchedulers} 同一慣例),
 * 「delta 鏈太長 / 沒有 baseline 就改跑 full snapshot」這條規則放在這裡。
 */
@Service
public class BloomGenerationService {

    private static final Logger log = LoggerFactory.getLogger(BloomGenerationService.class);

    private final BloomScopePlanner planner;
    private final BloomSnapshotService snapshots;
    private final BloomDeltaService deltas;
    private final BloomRetentionService retention;

    public BloomGenerationService(
            BloomScopePlanner planner,
            BloomSnapshotService snapshots,
            BloomDeltaService deltas,
            BloomRetentionService retention) {
        this.planner = planner;
        this.snapshots = snapshots;
        this.deltas = deltas;
        this.retention = retention;
    }

    /** 每日 full snapshot:重建全部 scope,再依保留份數清掉舊 artifact。 */
    public void runFullSnapshots() {
        snapshots.generateAll();
        retention.purgeAll();
    }

    /** 每小時 delta;單一 scope 失敗不影響其他 scope。 */
    public void runDeltas() {
        List<BloomTarget> targets = planner.targets();
        for (BloomTarget target : targets) {
            try {
                runDelta(target);
            } catch (RuntimeException e) {
                log.error(
                        "Bloom delta 生成失敗:{} / {}",
                        target.scope(),
                        target.tenantId().value(),
                        e);
            }
        }
    }

    private void runDelta(BloomTarget target) {
        DeltaOutcome outcome = deltas.generate(target);
        if (outcome.needsFullSnapshot()) {
            log.info(
                    "Bloom {}/{} 改以 full snapshot 生成({})",
                    target.scope(),
                    target.tenantId().value(),
                    outcome.status());
            snapshots.generate(target);
        }
    }
}
