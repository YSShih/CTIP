package com.ctip.application.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ctip.domain.plan.QuotaLimit;
import java.time.Duration;
import java.time.Instant;
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
