package com.ctip;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ctip.application.port.ClockPort;
import com.ctip.application.port.IdGeneratorPort;
import com.ctip.application.port.PlanRepository;
import com.ctip.application.port.SubscriptionRepository;
import com.ctip.domain.plan.Plan;
import com.ctip.domain.plan.PlanCode;
import com.ctip.support.TestPlans;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 記憶體限流(docs/spec/10-identity-plans.md §10.7,Phase 6):套用全端點、
 * 匿名超限回 429 + Retry-After;X-RateLimit-* 於所有回應(含成功)帶上。
 * 以縮小的 per-minute 配額測試,避免真實打 60 次。
 *
 * <p>配額自 Phase 14 起讀 {@code plans} 表的 ANONYMOUS 方案,不再是 property——
 * 因此改寫的是<strong>資料</strong>,測完必須還原:plans 是全域參考資料,
 * 整合測試共用同一個 context,留著被改小的配額會讓後續測試莫名 429。
 */
@AutoConfigureMockMvc
class RateLimitTest extends AbstractPostgresIntegrationTest {

    private static final int TEST_PER_MINUTE = 3;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PlanRepository plans;

    @Autowired
    private SubscriptionRepository subscriptions;

    @Autowired
    private IdGeneratorPort idGenerator;

    @Autowired
    private ClockPort clock;

    private TestPlans planAdmin;
    private Plan originalAnonymous;

    @BeforeEach
    void shrinkAnonymousQuota() {
        planAdmin = new TestPlans(plans, subscriptions, idGenerator, clock);
        originalAnonymous = planAdmin.plan(PlanCode.ANONYMOUS);
        planAdmin.save(TestPlans.requestsPerMinute(TEST_PER_MINUTE).apply(originalAnonymous));
    }

    @AfterEach
    void restoreAnonymousQuota() {
        planAdmin.save(originalAnonymous);
    }

    /**
     * 每個測試方法綁不同的 client IP。限流是 per-IP 的,若共用 127.0.0.1,
     * 先跑的測試會吃掉後跑測試的配額,失敗訊息還會指向無辜的那一個。
     */
    private static MockHttpServletRequestBuilder from(MockHttpServletRequestBuilder builder, String ip) {
        return builder.with(request -> {
            request.setRemoteAddr(ip);
            return request;
        });
    }

    @Test
    void anonymousIpExceedingLimitGets429WithHeadersOnEveryResponse() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(from(get("/api/v1/rate-limit-probe"), "198.51.100.1"))
                    .andExpect(status().isNotFound()) // Phase 9 前尚無 controller;限流在 filter 層已生效
                    .andExpect(header().string("X-RateLimit-Limit", "3"))
                    .andExpect(header().string("X-RateLimit-Remaining", String.valueOf(2 - i)))
                    .andExpect(header().exists("X-RateLimit-Reset"));
        }
        // 429 body 必須是統一錯誤結構(09 §9.4)——filter 手工組 JSON,欄位 drift 要被測試抓到
        mvc.perform(from(get("/api/v1/rate-limit-probe"), "198.51.100.1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("X-RateLimit-Limit", "3"))
                .andExpect(header().string("X-RateLimit-Remaining", "0"))
                .andExpect(header().exists("X-RateLimit-Reset"))
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.message").value("Rate limit exceeded"))
                .andExpect(jsonPath("$.path").value("/api/v1/rate-limit-probe"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.details").isArray());
    }

    /**
     * 迴歸鎖(Phase 13):憑證無效的請求<strong>一樣要計入限流</strong>。
     *
     * <p>認證 filter 在憑證無效時直接寫 401 並中止 chain;限流器若排在 security chain 之後就完全不會執行,
     * 只要掛一個亂寫的 Authorization 標頭即可無限量發送(每次都查一次資料庫)。
     * 實測曾為:75 次無效 token 全回 401、零個 429。
     */
    @Test
    void rejectedCredentialsStillConsumeTheRateLimitBudget() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(from(get("/api/v1/iocs").header("Authorization", "Bearer invalid-token-" + i), "198.51.100.2"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("X-RateLimit-Remaining", String.valueOf(2 - i)));
        }
        mvc.perform(from(get("/api/v1/iocs").header("Authorization", "Bearer invalid-token-final"), "198.51.100.2"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }

    /** 同上,X-API-Key 路徑也不得繞過(它每次都會查 api_keys 表)。 */
    @Test
    void rejectedApiKeysStillConsumeTheRateLimitBudget() throws Exception {
        String bogus = "ctip_mvp_" + "z".repeat(32);
        for (int i = 0; i < 3; i++) {
            mvc.perform(from(get("/api/v1/iocs").header("X-API-Key", bogus), "198.51.100.3"))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(from(get("/api/v1/iocs").header("X-API-Key", bogus), "198.51.100.3"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void actuatorProbesAreNotRateLimited() throws Exception {
        for (int i = 0; i < 10; i++) {
            mvc.perform(from(get("/actuator/health"), "198.51.100.4"))
                    .andExpect(status().isOk())
                    .andExpect(header().doesNotExist("X-RateLimit-Limit"));
        }
    }
}
