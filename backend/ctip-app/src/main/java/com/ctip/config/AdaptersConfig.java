package com.ctip.config;

import com.ctip.adapters.http.FetchResilience;
import com.ctip.adapters.http.ResiliencePolicy;
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
 * 排程只同步 enabled 的來源。
 */
@Configuration(proxyBeanMethods = false)
public class AdaptersConfig {

    @Bean
    FetchResilience fetchResilience() {
        return new FetchResilience(ResiliencePolicy.defaults());
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
