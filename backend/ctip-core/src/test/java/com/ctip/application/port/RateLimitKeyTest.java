package com.ctip.application.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ctip.domain.identity.ApiKeyId;
import com.ctip.domain.plan.EndpointClass;
import com.ctip.domain.plan.QuotaLimit;
import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.user.UserId;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** 限流鍵格式(docs/spec/10-identity-plans.md §10.7):ratelimit:{scope}:{subject}:{window}。 */
@Tag("unit")
class RateLimitKeyTest {

    @Test
    void asStringFollowsSpecFormat() {
        assertThat(RateLimitKey.anonymousIp("203.0.113.7", RateLimitKey.Window.MINUTE)
                        .asString())
                .isEqualTo("ratelimit:ip:203.0.113.7:minute");
        assertThat(new RateLimitKey("tenant", "t-123", RateLimitKey.Window.DAY).asString())
                .isEqualTo("ratelimit:tenant:t-123:day");
    }

    /** §10.7 的五個維度;1–3 是 Phase 17 加入的。 */
    @Test
    void everyDimensionHasItsOwnScope() {
        UUID id = UUID.fromString("11111111-2222-3333-4444-555555555555");
        assertThat(RateLimitKey.apiKey(new ApiKeyId(id), RateLimitKey.Window.MINUTE)
                        .asString())
                .isEqualTo("ratelimit:key:" + id + ":minute");
        assertThat(RateLimitKey.user(new UserId(id), RateLimitKey.Window.MINUTE).asString())
                .isEqualTo("ratelimit:user:" + id + ":minute");
        assertThat(RateLimitKey.tenant(new TenantId(id), RateLimitKey.Window.DAY)
                        .asString())
                .isEqualTo("ratelimit:tenant:" + id + ":day");
    }

    /**
     * 維度 5 的鍵<strong>含主體</strong>(ADR 0026):§10.7 字面上的
     * {@code ratelimit:{scope}:{endpointClass}:{window}} 是全平台共用一個桶,
     * 任何一個租戶打滿它就會拒絕所有人。
     */
    @Test
    void endpointClassKeyKeepsTheSubject() {
        RateLimitKey key = RateLimitKey.anonymousIp("203.0.113.7", RateLimitKey.Window.MINUTE)
                .inClass(EndpointClass.HEAVY);
        assertThat(key.asString()).isEqualTo("ratelimit:ip:203.0.113.7:heavy:minute");
        assertThat(key.inWindow(RateLimitKey.Window.DAY).asString()).isEqualTo("ratelimit:ip:203.0.113.7:heavy:day");
    }

    @Test
    void windowsCarryTheirDurations() {
        assertThat(RateLimitKey.Window.MINUTE.duration()).isEqualTo(Duration.ofMinutes(1));
        assertThat(RateLimitKey.Window.DAY.duration()).isEqualTo(Duration.ofDays(1));
    }

    @Test
    void rejectsNullComponents() {
        assertThatThrownBy(() -> new RateLimitKey(null, "x", RateLimitKey.Window.MINUTE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RateLimitKey("ip", null, RateLimitKey.Window.MINUTE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RateLimitKey("ip", "x", null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void resultCarriesHeaderData() {
        Instant reset = Instant.parse("2026-08-21T12:00:42Z");
        RateLimitResult result = new RateLimitResult(true, QuotaLimit.of(60L), 12, reset);
        assertThat(result.allowed()).isTrue();
        assertThat(result.limit().orElse(0)).isEqualTo(60);
        assertThat(result.remaining()).isEqualTo(12);
        assertThat(result.used()).isEqualTo(48);
        assertThat(result.resetAt()).isEqualTo(reset);
    }

    /** ENTERPRISE 的 requests_per_day 是「依合約」——標頭必須能表達無上限(ADR 0019)。 */
    @Test
    void unlimitedResultReportsNoCeiling() {
        RateLimitResult unlimited = RateLimitResult.unlimited(Instant.parse("2026-08-21T12:00:42Z"));
        assertThat(unlimited.allowed()).isTrue();
        assertThat(unlimited.limit().isUnlimited()).isTrue();
        assertThat(unlimited.used()).isZero();
    }
}
