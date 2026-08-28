package com.ctip.infrastructure.scheduling;

import com.ctip.application.bloom.BloomGenerationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Bloom 的兩個排程(docs/spec/11-sync-bloom.md §11.3):
 * full snapshot 每日一次(預設 04:00)、delta 日內每小時一次。
 *
 * <p>總開關沿用 {@code ctip.scheduler.enabled}(整合測試以 {@code SCHEDULER_ENABLED=false} 關閉);
 * 兩個 cron 值本身在 {@code ctip.bloom.*} 之下(§5.4.5 的 {@code BLOOM_*} 變數)。
 * 任務不含業務規則,只呼叫 application service 的單一方法。
 */
@Component
@ConditionalOnProperty(prefix = "ctip.scheduler", name = "enabled", havingValue = "true")
class BloomSchedulers {

    private final BloomGenerationService generation;

    BloomSchedulers(BloomGenerationService generation) {
        this.generation = generation;
    }

    @Scheduled(cron = "${ctip.bloom.snapshot-cron}")
    void generateFullSnapshots() {
        generation.runFullSnapshots();
    }

    @Scheduled(cron = "${ctip.bloom.delta-cron}")
    void generateDeltas() {
        generation.runDeltas();
    }
}
