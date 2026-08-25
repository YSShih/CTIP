package com.ctip.adapters.mock;

import java.util.List;

/**
 * 三個 mock 之間刻意重疊的 IOC(docs/spec/08-ingestion-sdk.md §8.3 要求 3,共 11 個),
 * 用於驗證多來源合併:含 confidence 差異大的、severity 不同的、TLP 不同的
 * (AbuseIPDB=GREEN vs AlienVault=CLEAR),以及一個被 AlienVault 標為撤回的。
 */
final class SharedIocs {

    private SharedIocs() {}

    static final String DOMAIN_HIGH_CONFIDENCE_GAP = "shared-phish-1.example.com";
    static final String DOMAIN_RETRACTED_BY_ALIENVAULT = "shared-phish-2.example.com";
    static final String DOMAIN_SEVERITY_GAP = "shared-phish-3.example.com";
    static final String DOMAIN_PLAIN = "shared-phish-4.example.com";

    static final String URL_LOGIN = "https://shared-campaign.example.net/login";
    static final String URL_VERIFY = "https://shared-campaign.example.net/verify";
    static final String URL_PAYLOAD = "http://shared-drop.example.org/payload";

    static final String IPV4_A = "198.51.100.23";
    static final String IPV4_B = "203.0.113.99";
    static final String IPV4_C = "192.0.2.77";
    static final String IPV6_A = "2001:db8:beef::42";

    static List<String> all() {
        return List.of(
                DOMAIN_HIGH_CONFIDENCE_GAP,
                DOMAIN_RETRACTED_BY_ALIENVAULT,
                DOMAIN_SEVERITY_GAP,
                DOMAIN_PLAIN,
                URL_LOGIN,
                URL_VERIFY,
                URL_PAYLOAD,
                IPV4_A,
                IPV4_B,
                IPV4_C,
                IPV6_A);
    }
}
