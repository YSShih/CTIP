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
 * 確定性 mock:IPv4 / IPv6 惡意位址(docs/spec/08-ingestion-sdk.md §8.3)。
 * 固定手寫資料集,零亂數;含刻意髒資料(前導零、空白、IPv6 全寫、無效 IP、
 * 私有/保留位址、批次內重複)與跨來源重疊 IP({@link SharedIocs};TLP 與
 * MockAlienVault 不同:GREEN vs CLEAR)。metadata 與 V4 種子對齊。
 */
public final class MockAbuseIPDBAdapter implements ThreatSourceAdapter {

    private static final int PAGE_SIZE = 6;

    private static final List<RawThreatRecord> DATASET = List.of(
            // 乾淨資料
            MockFeed.record("198.51.100.7", IocType.IPV4, "2026-08-01T12:00:00Z", 90, Severity.HIGH),
            MockFeed.record("203.0.113.15", IocType.IPV4, "2026-08-02T12:00:00Z", 85, Severity.HIGH),
            MockFeed.tagged("192.0.2.200", IocType.IPV4, "2026-08-03T12:00:00Z", 60, Set.of("brute-force", "ssh")),
            MockFeed.record("2001:db8:dead::1", IocType.IPV6, "2026-08-04T12:00:00Z", 75, Severity.HIGH),
            // 正規化髒資料(可接受,經清理後落庫)
            MockFeed.record("203.000.113.007", IocType.IPV4, "2026-08-05T12:00:00Z", 55, Severity.MEDIUM),
            MockFeed.record(" 198.51.100.99 ", IocType.IPV4, "2026-08-06T12:00:00Z", 50, Severity.MEDIUM),
            MockFeed.record(
                    "2001:0DB8:0000:0000:0000:0000:0000:0001",
                    IocType.IPV6,
                    "2026-08-07T12:00:00Z",
                    65,
                    Severity.MEDIUM),
            // 拒絕規則髒資料(docs/spec/07-domain-intel.md §7.3)
            MockFeed.record("999.1.2.3", IocType.IPV4, "2026-08-08T12:00:00Z", 40, Severity.LOW),
            MockFeed.record("192.168.1.50", IocType.IPV4, "2026-08-09T12:00:00Z", 45, Severity.LOW),
            MockFeed.record("fe80::1", IocType.IPV6, "2026-08-10T12:00:00Z", 35, Severity.LOW),
            MockFeed.record("10.0.0.8", IocType.IPV4, "2026-08-11T12:00:00Z", 30, Severity.LOW),
            MockFeed.record("198.51.100.55", IocType.IPV4, "2026-08-12T12:00:00Z", 70, Severity.MEDIUM),
            MockFeed.record("198.51.100.55", IocType.IPV4, "2026-08-12T12:30:00Z", 70, Severity.MEDIUM),
            // 跨來源重疊(與 MockAlienVault)
            MockFeed.record(SharedIocs.IPV4_A, IocType.IPV4, "2026-08-13T12:00:00Z", 95, Severity.HIGH),
            MockFeed.record(SharedIocs.IPV4_B, IocType.IPV4, "2026-08-14T12:00:00Z", 88, Severity.HIGH),
            MockFeed.record(SharedIocs.IPV4_C, IocType.IPV4, "2026-08-15T12:00:00Z", 70, Severity.MEDIUM),
            MockFeed.record(SharedIocs.IPV6_A, IocType.IPV6, "2026-08-16T12:00:00Z", 80, Severity.HIGH));

    @Override
    public SourceType sourceType() {
        return SourceType.MOCK_ABUSEIPDB;
    }

    @Override
    public SourceMetadata metadata() {
        return new SourceMetadata(
                "Mock AbuseIPDB",
                "確定性 mock 來源:IPv4 / IPv6 惡意位址(含刻意髒資料)",
                "https://abuseipdb.example.invalid",
                Set.of(IocType.IPV4, IocType.IPV6),
                Tlp.GREEN,
                RedistributionPolicy.DERIVED_ONLY,
                Duration.ofSeconds(3600),
                false);
    }

    @Override
    public FetchResult fetch(FetchContext context) {
        return MockFeed.page(DATASET, context, PAGE_SIZE);
    }
}
