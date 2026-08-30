package com.ctip.infrastructure.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctip.application.port.ClockPort;
import com.ctip.infrastructure.web.FilterErrorWriter;
import jakarta.servlet.FilterChain;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** {@code /actuator/prometheus} 的來源 IP 限制(docs/spec/13-platform-ops.md §13.6)。 */
@Tag("unit")
class PrometheusAccessFilterTest {

    private static final ClockPort CLOCK = () -> Instant.parse("2026-08-30T00:00:00Z");

    @Test
    void anAllowedSourceReachesTheEndpoint() throws Exception {
        MockHttpServletResponse response = scrape("172.20.0.5", List.of("172.16.0.0/12"));

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void anUnlistedSourceIsForbiddenWithTheUnifiedErrorShape() throws Exception {
        MockHttpServletResponse response = scrape("203.0.113.9", List.of("172.16.0.0/12"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("\"code\":\"FORBIDDEN\"");
    }

    /** 空白名單 = 拒絕所有來源(安全優先的預設方向)。 */
    @Test
    void anEmptyAllowlistRejectsEveryone() throws Exception {
        assertThat(scrape("127.0.0.1", List.of()).getStatus()).isEqualTo(403);
    }

    /** 其他 actuator 端點不受這道 filter 影響(它們由 ACTUATOR_EXPOSED_ENDPOINTS 控制)。 */
    @Test
    void otherActuatorEndpointsAreNotAffected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        request.setRemoteAddr("203.0.113.9");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter(List.of("127.0.0.1/32")).doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    private static MockHttpServletResponse scrape(String remoteAddr, List<String> allowed) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/prometheus");
        request.setRemoteAddr(remoteAddr);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter(allowed).doFilter(request, response, new MockFilterChain());
        return response;
    }

    private static PrometheusAccessFilter filter(List<String> allowed) {
        return new PrometheusAccessFilter(allowed, new FilterErrorWriter(CLOCK));
    }
}
