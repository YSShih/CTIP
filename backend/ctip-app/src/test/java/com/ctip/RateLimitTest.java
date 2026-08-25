package com.ctip;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 記憶體限流(docs/spec/10-identity-plans.md §10.7,Phase 6):套用全端點、
 * 匿名超限回 429 + Retry-After;X-RateLimit-* 於所有回應(含成功)帶上。
 * 以縮小的 per-minute 配額測試,避免真實打 60 次。
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = {"ctip.rate-limit.anonymous-per-minute=3", "ctip.rate-limit.anonymous-per-day=1000"})
class RateLimitTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void anonymousIpExceedingLimitGets429WithHeadersOnEveryResponse() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(get("/api/v1/rate-limit-probe"))
                    .andExpect(status().isNotFound()) // Phase 9 前尚無 controller;限流在 filter 層已生效
                    .andExpect(header().string("X-RateLimit-Limit", "3"))
                    .andExpect(header().string("X-RateLimit-Remaining", String.valueOf(2 - i)))
                    .andExpect(header().exists("X-RateLimit-Reset"));
        }
        mvc.perform(get("/api/v1/rate-limit-probe"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("X-RateLimit-Limit", "3"))
                .andExpect(header().string("X-RateLimit-Remaining", "0"))
                .andExpect(header().exists("X-RateLimit-Reset"))
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void actuatorProbesAreNotRateLimited() throws Exception {
        for (int i = 0; i < 10; i++) {
            mvc.perform(get("/actuator/health"))
                    .andExpect(status().isOk())
                    .andExpect(header().doesNotExist("X-RateLimit-Limit"));
        }
    }
}
