package com.ctip.infrastructure.scheduling;

import com.ctip.application.search.SearchReconciliationService;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 搜尋索引對帳排程(docs/spec/13-platform-ops.md §13.7:每日 05:00;08 §8.7 的
 * {@code ES_RECONCILE_CRON})。
 *
 * <p>與 {@code BloomSchedulers} 不同,本類別不是 {@code @Component}:它只在
 * {@code SEARCH_BACKEND=elasticsearch} 時才該存在(PostgreSQL 後端沒有外部索引可對帳),
 * 而兩個條件(搜尋後端 + {@code ctip.scheduler.enabled})的組合裝配在 {@code SearchConfig}。
 */
public class SearchSchedulers {

    private final SearchReconciliationService reconciliation;

    public SearchSchedulers(SearchReconciliationService reconciliation) {
        this.reconciliation = reconciliation;
    }

    @Scheduled(cron = "${ctip.search.reconcile-cron}")
    void reconcileSearchIndex() {
        reconciliation.reconcile();
    }
}
