package com.ctip.application.bloom;

import com.ctip.application.observability.BloomMetrics;
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
    private final BloomMetrics metrics;

    public BloomGenerationService(
            BloomScopePlanner planner,
            BloomSnapshotService snapshots,
            BloomDeltaService deltas,
            BloomRetentionService retention,
            BloomMetrics metrics) {
        this.planner = planner;
        this.snapshots = snapshots;
        this.deltas = deltas;
        this.retention = retention;
        this.metrics = metrics;
    }

    /**
     * 每日 full snapshot:重建全部 scope,再依保留份數清掉舊 artifact。
     * 逐一 scope 的迴圈在這裡而不在 {@link BloomSnapshotService}——
     * {@code ctip.bloom.generation.duration{scope}}(13 §13.6)要的是每個 scope 各自的耗時,
     * 而失敗隔離的粒度本來就是 scope(與 {@link #runDeltas()} 同一個形狀)。
     */
    public void runFullSnapshots() {
        for (BloomTarget target : planner.targets()) {
            try {
                metrics.time(target.scope(), () -> snapshots.generate(target));
            } catch (RuntimeException e) {
                log.error(
                        "Bloom full snapshot 生成失敗:{} / {}",
                        target.scope(),
                        target.tenantId().value(),
                        e);
            }
        }
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
        DeltaOutcome outcome = metrics.time(target.scope(), () -> deltas.generate(target));
        if (outcome.needsFullSnapshot()) {
            log.info(
                    "Bloom {}/{} 改以 full snapshot 生成({})",
                    target.scope(),
                    target.tenantId().value(),
                    outcome.status());
            metrics.time(target.scope(), () -> snapshots.generate(target));
        }
    }
}
