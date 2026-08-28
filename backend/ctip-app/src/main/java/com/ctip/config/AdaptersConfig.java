package com.ctip.config;

import com.ctip.adapters.http.FetchResilience;
import com.ctip.adapters.http.ResiliencePolicy;
import com.ctip.adapters.manual.ManualSubmissionAdapter;
import com.ctip.adapters.mock.MockAbuseIPDBAdapter;
import com.ctip.adapters.mock.MockAlienVaultAdapter;
import com.ctip.adapters.mock.MockOpenPhishAdapter;
import com.ctip.sdk.ThreatSourceAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Adapter 裝配:ctip-adapters 刻意無 Spring 相依(第三方只需依賴 ctip-sdk),
 * bean 在此宣告;韌性(docs/spec/08-ingestion-sdk.md §8.5)於註冊前以組態方式統一套用。
 * 三個 mock 全部註冊——啟用與否由 sources.enabled 決定(V4 種子:MVP 只啟用 MOCK_OPENPHISH),
 * 排程只同步 enabled 的來源;MANUAL 來源 syncable = false,不參與排程。
 */
@Configuration(proxyBeanMethods = false)
public class AdaptersConfig {

    @Bean
    FetchResilience fetchResilience() {
        return new FetchResilience(ResiliencePolicy.defaults());
    }

    /**
     * 手動提交／匯入(§8.3)。<strong>不套韌性裝配</strong>:retry / circuit breaker / bulkhead
     * 是為外部 HTTP 來源而設,本 adapter 只做記憶體內的解碼——重試一次解碼失敗的 CSV 沒有意義,
     * 而斷路器一開就會讓使用者的提交憑空消失。
     */
    @Bean
    ThreatSourceAdapter manualSubmissionAdapter() {
        return new ManualSubmissionAdapter();
    }

    @Bean
    ThreatSourceAdapter mockOpenPhishAdapter(FetchResilience resilience) {
        return resilience.decorate(new MockOpenPhishAdapter());
    }

    @Bean
    ThreatSourceAdapter mockAbuseIpdbAdapter(FetchResilience resilience) {
        return resilience.decorate(new MockAbuseIPDBAdapter());
    }

    @Bean
    ThreatSourceAdapter mockAlienVaultAdapter(FetchResilience resilience) {
        return resilience.decorate(new MockAlienVaultAdapter());
    }
}
