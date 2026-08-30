package com.ctip.infrastructure.scheduling;

import com.ctip.infrastructure.observability.SourceSyncLagBinder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * {@code ctip.source.sync.lag{source}} 的來源清單重整(docs/spec/13-platform-ops.md §13.6)。
 * gauge 的<strong>值</strong>每次抓取都會重算,這裡重整的是「有哪些來源」——
 * 管理端點新增或停用來源之後,序列集合才會跟上。
 */
@Component
@ConditionalOnProperty(prefix = "ctip.scheduler", name = "enabled", havingValue = "true")
class MetricsSchedulers {

    private final SourceSyncLagBinder sourceSyncLag;

    MetricsSchedulers(SourceSyncLagBinder sourceSyncLag) {
        this.sourceSyncLag = sourceSyncLag;
    }

    @Scheduled(fixedDelayString = "${ctip.observability.source-lag-refresh-ms:60000}")
    void refreshSourceSyncLag() {
        sourceSyncLag.refresh();
    }
}
