package com.ctip.adapters.mock;

import com.ctip.sdk.FetchContext;
import com.ctip.sdk.FetchResult;
import com.ctip.sdk.IocType;
import com.ctip.sdk.RawThreatRecord;
import com.ctip.sdk.RedistributionPolicy;
import com.ctip.sdk.Severity;
import com.ctip.sdk.SourceMetadata;
import com.ctip.sdk.SourceType;
import com.ctip.sdk.ThreatSourceAdapter;
import com.ctip.sdk.Tlp;
import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * 確定性 mock:URL / Domain 型釣魚情資(docs/spec/08-ingestion-sdk.md §8.3)。
 * 固定手寫資料集,零亂數;含刻意髒資料(大小寫、空白、零寬字元、超長 URL、
 * allowlist 網域、批次內重複、無法推斷型別)與跨來源重疊 IOC({@link SharedIocs})。
 * metadata 與 V4 種子(V4__seed_sources.sql)對齊。
 */
public final class MockOpenPhishAdapter implements ThreatSourceAdapter {

    private static final int PAGE_SIZE = 8;

    private static final List<RawThreatRecord> DATASET = List.of(
            // 乾淨資料
            MockFeed.record(
                    "https://login.paypa1-secure.example.com/session",
                    IocType.URL,
                    "2026-08-01T06:00:00Z",
                    85,
                    Severity.HIGH),
            MockFeed.record(
                    "https://update-account.example-bank.net/verify",
                    IocType.URL,
                    "2026-08-02T06:00:00Z",
                    80,
                    Severity.HIGH),
            MockFeed.record("malware-delivery.example.io", IocType.DOMAIN, "2026-08-03T06:00:00Z", 75, Severity.MEDIUM),
            MockFeed.tagged(
                    "credential-harvest.example.biz",
                    IocType.DOMAIN,
                    "2026-08-04T06:00:00Z",
                    70,
                    Set.of("phishing", "credential-theft")),
            // 正規化髒資料(可接受,經清理後落庫)
            MockFeed.record(
                    "  https://trailing-space.example.com/login  ",
                    IocType.URL,
                    "2026-08-05T06:00:00Z",
                    65,
                    Severity.MEDIUM),
            MockFeed.record(
                    "HTTPS://UPPER-CASE.EXAMPLE.COM:443/Path?b=2&a=1#section",
                    IocType.URL,
                    "2026-08-06T06:00:00Z",
                    60,
                    Severity.MEDIUM),
            MockFeed.record("zero\u200bwidth.example.net", IocType.DOMAIN, "2026-08-07T06:00:00Z", 55, Severity.LOW),
            MockFeed.record("Trailing-Dot.Example.COM.", IocType.DOMAIN, "2026-08-08T06:00:00Z", 50, Severity.LOW),
            // 拒絕規則髒資料(docs/spec/07-domain-intel.md §7.3)
            MockFeed.record(
                    "https://long.example.com/" + "a".repeat(2100),
                    IocType.URL,
                    "2026-08-09T06:00:00Z",
                    40,
                    Severity.LOW),
            MockFeed.record("allowlisted.example.com", IocType.DOMAIN, "2026-08-10T06:00:00Z", 45, Severity.LOW),
            MockFeed.record(
                    "https://duplicate.example.com/kit", IocType.URL, "2026-08-11T06:00:00Z", 70, Severity.HIGH),
            MockFeed.record(
                    "https://duplicate.example.com/kit", IocType.URL, "2026-08-11T06:30:00Z", 70, Severity.HIGH),
            MockFeed.record("%%%not-an-ioc%%%", null, "2026-08-12T06:00:00Z", 30, Severity.INFO),
            // 跨來源重疊(與 MockAlienVault;confidence / severity 刻意拉開)
            MockFeed.record(
                    SharedIocs.DOMAIN_HIGH_CONFIDENCE_GAP, IocType.DOMAIN, "2026-08-13T06:00:00Z", 90, Severity.HIGH),
            MockFeed.record(
                    SharedIocs.DOMAIN_RETRACTED_BY_ALIENVAULT,
                    IocType.DOMAIN,
                    "2026-08-14T06:00:00Z",
                    85,
                    Severity.HIGH),
            MockFeed.record(
                    SharedIocs.DOMAIN_SEVERITY_GAP, IocType.DOMAIN, "2026-08-15T06:00:00Z", 60, Severity.MEDIUM),
            MockFeed.record(SharedIocs.DOMAIN_PLAIN, IocType.DOMAIN, "2026-08-16T06:00:00Z", 55, Severity.MEDIUM),
            MockFeed.record(SharedIocs.URL_LOGIN, IocType.URL, "2026-08-17T06:00:00Z", 88, Severity.HIGH),
            MockFeed.record(SharedIocs.URL_VERIFY, IocType.URL, "2026-08-18T06:00:00Z", 82, Severity.HIGH),
            MockFeed.record(SharedIocs.URL_PAYLOAD, IocType.URL, "2026-08-19T06:00:00Z", 77, Severity.MEDIUM));

    @Override
    public SourceType sourceType() {
        return SourceType.MOCK_OPENPHISH;
    }

    @Override
    public SourceMetadata metadata() {
        return new SourceMetadata(
                "Mock OpenPhish",
                "確定性 mock 來源:URL / Domain 型釣魚情資(含刻意髒資料)",
                "https://openphish.example.invalid",
                Set.of(IocType.URL, IocType.DOMAIN),
                Tlp.CLEAR,
                RedistributionPolicy.ATTRIBUTION_REQUIRED,
                Duration.ofSeconds(3600),
                false);
    }

    @Override
    public FetchResult fetch(FetchContext context) {
        return MockFeed.page(DATASET, context, PAGE_SIZE);
    }
}
