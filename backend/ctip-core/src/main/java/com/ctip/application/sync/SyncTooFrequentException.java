package com.ctip.application.sync;

import java.time.Duration;

/**
 * 同步過於頻繁(docs/spec/11-sync-bloom.md §11.6:受 {@code plans.min_sync_interval_seconds}
 * 限制,過於頻繁回 {@code 429})。
 *
 * <p>依 09 §9.7 的三種語意,這是「時間窗內的計數」那一類——它會自己恢復,
 * 因此必須帶 {@code Retry-After};而不是 {@code 403 PLAN_LIMIT_EXCEEDED}(等待無用的能力上限)。
 */
public class SyncTooFrequentException extends RuntimeException {

    @java.io.Serial
    private static final long serialVersionUID = 1L;

    private final transient Duration retryAfter;
    private final transient Duration minInterval;

    public SyncTooFrequentException(Duration retryAfter, Duration minInterval) {
        super("Sync interval of " + minInterval.toSeconds() + "s has not elapsed yet");
        this.retryAfter = retryAfter;
        this.minInterval = minInterval;
    }

    /** 距離下次可同步還剩多久(至少 1 秒:{@code Retry-After: 0} 會被 client 讀成「立刻重試」)。 */
    public Duration retryAfter() {
        return retryAfter.isNegative() || retryAfter.isZero() ? Duration.ofSeconds(1) : retryAfter;
    }

    public Duration minInterval() {
        return minInterval;
    }
}
