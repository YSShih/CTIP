package com.ctip.interfaces.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ctip.AbstractPostgresIntegrationTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * CORS preflight 必須穿過 Spring Security filter chain(Phase 13 起 chain 存在)。
 * 前端是獨立來源的 SPA,preflight 一旦被擋,所有跨源呼叫在瀏覽器端全滅——
 * 這是只有整合層才驗得到的接線(05 §5.7、ADR 0009 曾因缺 MVC 接線踩過一次)。
 */
@AutoConfigureMockMvc
class CorsPreflightTest extends AbstractPostgresIntegrationTest {

    private static final String ORIGIN = "http://localhost:5173";

    @Autowired
    private MockMvc mvc;

    @ParameterizedTest
    @ValueSource(strings = {"GET", "POST", "DELETE"})
    void preflightIsAllowedForTheConfiguredOriginAndMethods(String method) throws Exception {
        mvc.perform(options("/api/v1/api-keys")
                        .header("Origin", ORIGIN)
                        .header("Access-Control-Request-Method", method)
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN));
    }

    @ParameterizedTest
    @ValueSource(strings = {"X-RateLimit-Limit", "X-RateLimit-Remaining", "X-RateLimit-Reset", "Retry-After"})
    void rateLimitHeadersRemainReadableFromTheBrowser(String exposed) throws Exception {
        mvc.perform(options("/api/v1/iocs").header("Origin", ORIGIN).header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().stringValues(
                                "Access-Control-Expose-Headers",
                                org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString(exposed))));
    }

    @org.junit.jupiter.api.Test
    void unknownOriginIsRejected() throws Exception {
        mvc.perform(options("/api/v1/iocs")
                        .header("Origin", "http://evil.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }
}
