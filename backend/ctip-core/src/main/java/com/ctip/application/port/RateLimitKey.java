package com.ctip.application.port;

import java.time.Duration;
import java.util.Objects;

/**
 * 限流鍵(docs/spec/10-identity-plans.md §10.7):{@code ratelimit:{scope}:{subject}:{window}}。
 * M1 只有匿名 IP 維度(scope = "ip");M2 起增加 key/user/tenant 維度,由最 specific
 * 到最 general 依序檢查,任一超限即拒絕。
 */
public record RateLimitKey(String scope, String subject, Window window) {

    public RateLimitKey {
        Objects.requireNonNull(scope, "scope 不得為 null");
        Objects.requireNonNull(subject, "subject 不得為 null");
        Objects.requireNonNull(window, "window 不得為 null");
    }

    public static RateLimitKey anonymousIp(String normalizedIp, Window window) {
        return new RateLimitKey("ip", normalizedIp, window);
    }

    /**
     * 手動提交的每日配額(§10.6 {@code max_manual_submissions_per_day})。
     * 它是「時間窗內的計數」,依 §9.7 的三種語意走 429 + Retry-After,
     * 因此與請求限流共用同一套視窗機制,而非另做一張計數表(ADR 0023)。
     */
    public static RateLimitKey manualSubmissions(java.util.UUID tenantId) {
        return new RateLimitKey("submit", tenantId.toString(), Window.DAY);
    }

    public String asString() {
        return "ratelimit:" + scope + ":" + subject + ":" + window.name().toLowerCase(java.util.Locale.ROOT);
    }

    /** 限流視窗(§10.7:window ∈ {minute, day})。 */
    public enum Window {
        MINUTE(Duration.ofMinutes(1)),
        DAY(Duration.ofDays(1));

        private final Duration duration;

        Window(Duration duration) {
            this.duration = duration;
        }

        public Duration duration() {
            return duration;
        }
    }
}
