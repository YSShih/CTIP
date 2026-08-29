package com.ctip.interfaces.rest;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ctip.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * CORS preflight 必須穿過 Spring Security filter chain(Phase 13 起 chain 存在)。
 * 前端是獨立來源的 SPA,preflight 一旦被擋,所有跨源呼叫在瀏覽器端全滅——
 * 這是只有整合層才驗得到的接線(05 §5.7、ADR 0009 曾因缺 MVC 接線踩過一次)。
 */
@AutoConfigureMockMvc
class CorsPreflightTest extends AbstractPostgresIntegrationTest {

    private static final String ORIGIN = "http://localhost:5173";
    private static final String CLIENT_IP = "198.51.100.9";

    @Autowired
    private MockMvc mvc;

    /** 獨立 client IP:限流是 per-IP 的,與同 context 的其他測試共用會互相吃掉配額。 */
    private static MockHttpServletRequestBuilder from(MockHttpServletRequestBuilder builder) {
        return builder.with(request -> {
            request.setRemoteAddr(CLIENT_IP);
            return request;
        });
    }

    private static MockHttpServletRequestBuilder preflight(String path, String method) {
        return from(options(path).header("Origin", ORIGIN).header("Access-Control-Request-Method", method));
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "POST", "PUT", "PATCH", "DELETE"})
    void preflightIsAllowedForTheConfiguredOriginAndMethods(String method) throws Exception {
        mvc.perform(preflight("/api/v1/api-keys", method).header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN));
    }

    /**
     * 迴歸鎖:每一個實際存在的 HTTP 方法都必須在 CORS 清單裡。
     *
     * <p>{@code PATCH /notifications/{id}/read}(Phase 20)與 {@code PUT /threats/{id}/status}
     * (Phase 18)曾因清單只有 GET/POST/DELETE 而在瀏覽器端完全打不通:preflight 403,
     * 伺服器端測試卻全綠——只有這一層驗得到。
     */
    @ParameterizedTest
    @ValueSource(strings = {"PATCH", "PUT"})
    void preflightIsAllowedForTheMethodsUsedByWriteEndpoints(String method) throws Exception {
        mvc.perform(preflight("/api/v1/notifications/" + java.util.UUID.randomUUID() + "/read", method)
                        .header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN));
    }

    @ParameterizedTest
    @ValueSource(strings = {"X-RateLimit-Limit", "X-RateLimit-Remaining", "X-RateLimit-Reset", "Retry-After"})
    void rateLimitHeadersRemainReadableFromTheBrowser(String exposed) throws Exception {
        mvc.perform(preflight("/api/v1/iocs", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().stringValues("Access-Control-Expose-Headers", hasItem(containsString(exposed))));
    }

    @Test
    void unknownOriginIsRejected() throws Exception {
        mvc.perform(from(options("/api/v1/iocs")
                        .header("Origin", "http://evil.example")
                        .header("Access-Control-Request-Method", "GET")))
                .andExpect(status().isForbidden());
    }

    /**
     * 迴歸鎖(Phase 13):preflight 不計入限流配額。
     *
     * <p>它是瀏覽器自動產生的額外往返,計入等於把 SPA 的可用配額砍半;
     * 但判定必須窄到「帶 Access-Control-Request-Method 的 OPTIONS」,否則一般 OPTIONS 就成了繞過洞。
     */
    @Test
    void preflightDoesNotConsumeTheRateLimitBudget() throws Exception {
        for (int i = 0; i < 80; i++) {
            mvc.perform(preflight("/api/v1/iocs", "GET")).andExpect(status().isOk());
        }
    }
}
