package com.ctip.infrastructure.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctip.application.port.ClockPort;
import com.ctip.application.port.RateLimitKey;
import com.ctip.application.port.RateLimitResult;
import com.ctip.application.port.RateLimiterPort;
import com.ctip.domain.plan.Plan;
import com.ctip.domain.plan.PlanCode;
import com.ctip.domain.plan.PlanId;
import com.ctip.domain.plan.QuotaLimit;
import com.ctip.infrastructure.web.FilterErrorWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * RateLimitFilter 單元行為(§10.7「依序檢查,任一超限即拒絕」):
 * minute 超限的請求不得消耗 day 配額(否則被 429 的猛打流量會燒光整個 IP 的日配額);
 * 429 手工 JSON 的 path 為 client 可控值,必須跳脫;/actuator 豁免不得被路徑穿越繞過。
 */
@Tag("unit")
class RateLimitFilterTest {

    private static final Instant NOW = Instant.parse("2026-08-27T12:00:00Z");
    private static final ClockPort CLOCK = () -> NOW;

    /** §10.6 匿名列:60/min、1000/day。Phase 14 起這兩個值來自 plans 表,不再是 property。 */
    private static final Plan ANONYMOUS_PLAN = new Plan(
            new PlanId(java.util.UUID.nameUUIDFromBytes("anonymous".getBytes(java.nio.charset.StandardCharsets.UTF_8))),
            PlanCode.ANONYMOUS,
            "Anonymous",
            0,
            QuotaLimit.of(60L),
            QuotaLimit.of(1000L),
            50,
            QuotaLimit.of(20L),
            86400,
            true,
            QuotaLimit.unlimited(),
            false,
            QuotaLimit.disabled(),
            QuotaLimit.disabled(),
            false,
            QuotaLimit.disabled(),
            QuotaLimit.disabled(),
            QuotaLimit.disabled());

    /** 記錄每次消耗的 window;minute 一律拒絕。 */
    private static final class MinuteRejectingLimiter implements RateLimiterPort {
        private final List<RateLimitKey.Window> consumed = new ArrayList<>();

        @Override
        public RateLimitResult tryConsume(RateLimitKey key, int tokens, QuotaLimit limit) {
            consumed.add(key.window());
            boolean minute = key.window() == RateLimitKey.Window.MINUTE;
            return new RateLimitResult(!minute, limit, minute ? 0 : 999, NOW.plusSeconds(30));
        }

        @Override
        public RateLimitResult peek(RateLimitKey key, QuotaLimit limit) {
            throw new UnsupportedOperationException("filter 不使用 peek");
        }
    }

    @Test
    void minuteRejectionDoesNotConsumeDayQuota() throws Exception {
        MinuteRejectingLimiter limiter = new MinuteRejectingLimiter();
        RateLimitFilter filter = new RateLimitFilter(limiter, () -> ANONYMOUS_PLAN, true, CLOCK);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("/api/v1/iocs"), response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(limiter.consumed).containsExactly(RateLimitKey.Window.MINUTE);
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("0");
        assertThat(response.getHeader("Retry-After")).isEqualTo("30");
    }

    @Test
    void rejectionBodyEscapesClientControlledPath() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(new MinuteRejectingLimiter(), () -> ANONYMOUS_PLAN, true, CLOCK);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("/api/v1/\"},\\evil"), response, new MockFilterChain());

        assertThat(response.getContentAsString())
                .contains("\"path\":\"/api/v1/\\\"},\\\\evil\"")
                .contains("\"code\":\"RATE_LIMIT_EXCEEDED\"");
    }

    @Test
    void escapeJsonHandlesQuotesBackslashesAndControlCharacters() {
        assertThat(FilterErrorWriter.escapeJson("a\"b\\c\nd")).isEqualTo("a\\\"b\\\\c\\u000ad");
        assertThat(FilterErrorWriter.escapeJson("/plain/path")).isEqualTo("/plain/path");
    }

    @Test
    void actuatorExemptionIsNotBypassableByPathTraversal() {
        RateLimitFilter filter = new RateLimitFilter(new MinuteRejectingLimiter(), () -> ANONYMOUS_PLAN, true, CLOCK);
        assertThat(filter.shouldNotFilter(request("/actuator/health"))).isTrue();
        assertThat(filter.shouldNotFilter(request("/actuator/../api/v1/iocs"))).isFalse();
    }

    private static MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRemoteAddr("203.0.113.7");
        return request;
    }
}
