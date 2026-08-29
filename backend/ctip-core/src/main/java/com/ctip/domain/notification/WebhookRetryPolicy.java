package com.ctip.domain.notification;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * 不變量 W4:送達重試最多五次,指數退避(docs/spec/02-ddd-model.md §2.3、13 §13.2)。
 *
 * <p>退避表為 {@code 1、2、4、8} 分鐘(第 1→2、2→3、3→4、4→5 次嘗試之間)。
 * 實際觸發由每 5 分鐘的 {@code NOTIFICATION_RETRY_CRON} 掃描(08 §8.7),
 * 因此前兩段的退避會被排程粒度吸收——退避表定義的是<strong>最早</strong>可重試的時點,
 * 不是保證的間隔。
 */
public final class WebhookRetryPolicy {

    private static final Duration INITIAL_BACKOFF = Duration.ofMinutes(1);

    private WebhookRetryPolicy() {}

    /**
     * 第 {@code failedAttempt} 次嘗試失敗後,下一次可重試的時點。
     *
     * @return {@link Optional#empty()} 表示已用盡 {@link Webhook#MAX_ATTEMPTS} 次,該事件轉 {@code ABANDONED}
     */
    public static Optional<Instant> nextRetryAt(int failedAttempt, Instant now) {
        if (failedAttempt < 1) {
            throw new IllegalArgumentException("attempt 必須 >= 1:" + failedAttempt);
        }
        if (failedAttempt >= Webhook.MAX_ATTEMPTS) {
            return Optional.empty();
        }
        return Optional.of(now.plus(INITIAL_BACKOFF.multipliedBy(1L << (failedAttempt - 1))));
    }
}
