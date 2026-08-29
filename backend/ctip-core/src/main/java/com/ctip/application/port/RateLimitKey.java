package com.ctip.application.port;

import com.ctip.domain.identity.ApiKeyId;
import com.ctip.domain.plan.EndpointClass;
import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.user.UserId;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

/**
 * 限流鍵(docs/spec/10-identity-plans.md §10.7):{@code ratelimit:{scope}:{subject}:{window}}。
 * 五個維度由最 specific 到最 general 依序檢查,任一超限即拒絕:
 * key(1)→ user(2)→ tenant(3)→ ip(4)→ endpointClass(5)。
 *
 * <p><strong>維度 5 的鍵含 subject(規格偏離,ADR 0026)</strong>:§10.7 寫的是
 * {@code ratelimit:{scope}:{endpointClass}:{window}}——沒有 subject。照字面實作,
 * {@code ratelimit:tenant:read:minute} 是<strong>全平台共用一個桶</strong>,
 * 任何一個租戶(或任何一個匿名 IP)打滿它就會讓所有人被拒。ADR 0020 定調比例上限的用意是
 * 「分類上限恆低於該主體的總上限」,那必須是 per-subject 才成立。
 */
public record RateLimitKey(String scope, String subject, EndpointClass endpointClass, Window window) {

    public RateLimitKey {
        Objects.requireNonNull(scope, "scope 不得為 null");
        Objects.requireNonNull(subject, "subject 不得為 null");
        Objects.requireNonNull(window, "window 不得為 null");
    }

    public RateLimitKey(String scope, String subject, Window window) {
        this(scope, subject, null, window);
    }

    /** 維度 1:API key(§10.7 {@code ratelimit:key:{apiKeyId}:{window}})。 */
    public static RateLimitKey apiKey(ApiKeyId apiKeyId, Window window) {
        return new RateLimitKey("key", apiKeyId.value().toString(), window);
    }

    /** 維度 2:使用者。 */
    public static RateLimitKey user(UserId userId, Window window) {
        return new RateLimitKey("user", userId.value().toString(), window);
    }

    /** 維度 3:租戶。 */
    public static RateLimitKey tenant(TenantId tenantId, Window window) {
        return new RateLimitKey("tenant", tenantId.value().toString(), window);
    }

    /** 維度 4:匿名 IP(IPv6 已收斂至 /64)。 */
    public static RateLimitKey anonymousIp(String normalizedIp, Window window) {
        return new RateLimitKey("ip", normalizedIp, window);
    }

    /** 維度 5:同一主體在某個端點類別上的上限。 */
    public RateLimitKey inClass(EndpointClass endpointClass) {
        return new RateLimitKey(scope, subject, Objects.requireNonNull(endpointClass), window);
    }

    public RateLimitKey inWindow(Window other) {
        return new RateLimitKey(scope, subject, endpointClass, other);
    }

    /**
     * 手動提交的每日配額(§10.6 {@code max_manual_submissions_per_day})。
     * 它是「時間窗內的計數」,依 §9.7 的三種語意走 429 + Retry-After,
     * 因此與請求限流共用同一套視窗機制,而非另做一張計數表(ADR 0023)。
     */
    public static RateLimitKey manualSubmissions(UUID tenantId) {
        return new RateLimitKey("submit", tenantId.toString(), Window.DAY);
    }

    public String asString() {
        String classSegment = endpointClass == null ? "" : ":" + endpointClass.keySegment();
        return "ratelimit:" + scope + ":" + subject + classSegment + ":"
                + window.name().toLowerCase(java.util.Locale.ROOT);
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
