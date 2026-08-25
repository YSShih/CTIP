package com.ctip.adapters.mock;

import com.ctip.sdk.FetchContext;
import com.ctip.sdk.FetchResult;
import com.ctip.sdk.IocHashType;
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
 * 確定性 mock:混合型別 + STIX 風格 payload(docs/spec/08-ingestion-sdk.md §8.3)。
 * 固定手寫資料集,零亂數;與另兩個 mock 刻意重疊 11 個 IOC({@link SharedIocs}),
 * 其中 {@code shared-phish-2.example.com} 以 STIX {@code revoked=true} 標為撤回。
 * 另含 FILE_HASH / EMAIL 髒資料(大小寫、雜湊長度不符、超長 email)。metadata 與 V4 種子對齊。
 */
public final class MockAlienVaultAdapter implements ThreatSourceAdapter {

    private static final int PAGE_SIZE = 7;

    private static final List<RawThreatRecord> DATASET = List.of(
            // 跨來源重疊:網域(與 MockOpenPhish;confidence / severity 刻意拉開)
            MockFeed.stix(
                    SharedIocs.DOMAIN_HIGH_CONFIDENCE_GAP, IocType.DOMAIN, "2026-08-13T18:00:00Z", 25, Severity.LOW),
            MockFeed.stixRevoked(
                    SharedIocs.DOMAIN_RETRACTED_BY_ALIENVAULT,
                    IocType.DOMAIN,
                    "2026-08-14T18:00:00Z",
                    60,
                    Severity.MEDIUM),
            MockFeed.stix(
                    SharedIocs.DOMAIN_SEVERITY_GAP, IocType.DOMAIN, "2026-08-15T18:00:00Z", 65, Severity.CRITICAL),
            MockFeed.stix(SharedIocs.DOMAIN_PLAIN, IocType.DOMAIN, "2026-08-16T18:00:00Z", 50, Severity.MEDIUM),
            // 跨來源重疊:URL(與 MockOpenPhish)
            MockFeed.stix(SharedIocs.URL_LOGIN, IocType.URL, "2026-08-17T18:00:00Z", 40, Severity.LOW),
            MockFeed.stix(SharedIocs.URL_VERIFY, IocType.URL, "2026-08-18T18:00:00Z", 30, Severity.LOW),
            MockFeed.stix(SharedIocs.URL_PAYLOAD, IocType.URL, "2026-08-19T18:00:00Z", 55, Severity.MEDIUM),
            // 跨來源重疊:IP(與 MockAbuseIPDB;TLP 不同:CLEAR vs GREEN)
            MockFeed.stix(SharedIocs.IPV4_A, IocType.IPV4, "2026-08-13T18:30:00Z", 40, Severity.LOW),
            MockFeed.stix(SharedIocs.IPV4_B, IocType.IPV4, "2026-08-14T18:30:00Z", 35, Severity.LOW),
            MockFeed.stix(SharedIocs.IPV4_C, IocType.IPV4, "2026-08-15T18:30:00Z", 45, Severity.MEDIUM),
            MockFeed.stix(SharedIocs.IPV6_A, IocType.IPV6, "2026-08-16T18:30:00Z", 50, Severity.MEDIUM),
            // 獨有:FILE_HASH(乾淨 + 大小寫正規化 + MD5)
            MockFeed.hash(
                    "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
                    IocHashType.SHA256,
                    "2026-08-05T18:00:00Z",
                    90,
                    Severity.CRITICAL),
            MockFeed.hash(
                    "2C26B46B68FFC68FF99B453C1D30413413422D706483BFA0F98A5E886266E7AE",
                    IocHashType.SHA256,
                    "2026-08-06T18:00:00Z",
                    85,
                    Severity.HIGH),
            MockFeed.hash(
                    "d41d8cd98f00b204e9800998ecf8427e", IocHashType.MD5, "2026-08-07T18:00:00Z", 70, Severity.MEDIUM),
            // 拒絕規則髒資料:宣告 SHA256 但長度為 40(HASH_LENGTH_MISMATCH)、非十六進位(MALFORMED_VALUE)
            MockFeed.hash(
                    "da39a3ee5e6b4b0d3255bfef95601890afd80709",
                    IocHashType.SHA256,
                    "2026-08-08T18:00:00Z",
                    60,
                    Severity.MEDIUM),
            MockFeed.hash("zz-not-hex-zz", null, "2026-08-09T18:00:00Z", 40, Severity.LOW),
            // 獨有:EMAIL(local part 保留大小寫、domain 小寫;超長 email → LENGTH_EXCEEDED)
            MockFeed.record("Spear.Phisher@EXAMPLE.ORG", IocType.EMAIL, "2026-08-10T18:00:00Z", 65, Severity.MEDIUM),
            MockFeed.record(
                    "dropper@malware-mail.example.com", IocType.EMAIL, "2026-08-11T18:00:00Z", 60, Severity.MEDIUM),
            MockFeed.record(
                    "x".repeat(310) + "@long.example.com", IocType.EMAIL, "2026-08-12T18:00:00Z", 30, Severity.LOW));

    @Override
    public SourceType sourceType() {
        return SourceType.MOCK_ALIENVAULT;
    }

    @Override
    public SourceMetadata metadata() {
        return new SourceMetadata(
                "Mock AlienVault OTX",
                "確定性 mock 來源:混合型別 + STIX 風格 payload(含刻意髒資料)",
                "https://otx.alienvault.example.invalid",
                Set.of(IocType.IPV4, IocType.IPV6, IocType.DOMAIN, IocType.URL, IocType.FILE_HASH, IocType.EMAIL),
                Tlp.CLEAR,
                RedistributionPolicy.PUBLIC_REDISTRIBUTABLE,
                Duration.ofSeconds(3600),
                false);
    }

    @Override
    public FetchResult fetch(FetchContext context) {
        return MockFeed.page(DATASET, context, PAGE_SIZE);
    }
}
