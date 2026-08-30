package com.ctip.infrastructure.scheduling;

import com.ctip.application.identity.ExpiredTokenCleanupService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 身分相關的排程(docs/spec/08-ingestion-sdk.md §8.7)。與其他排程類別同一個形狀:
 * 只做一件事、只呼叫一個 application service 的方法,任務類別本身不含業務邏輯。
 */
@Component
@ConditionalOnProperty(prefix = "ctip.scheduler", name = "enabled", havingValue = "true")
public class IdentitySchedulers {

    private final ExpiredTokenCleanupService tokenCleanup;

    public IdentitySchedulers(ExpiredTokenCleanupService tokenCleanup) {
        this.tokenCleanup = tokenCleanup;
    }

    /** 過期 token 清理:每日 02:00(TOKEN_CLEANUP_CRON)。 */
    @Scheduled(cron = "${ctip.scheduler.token-cleanup-cron}")
    void revokeExpiredTokens() {
        tokenCleanup.revokeExpiredTokens();
    }
}
