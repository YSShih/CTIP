package com.ctip.support;

import com.ctip.application.port.IndicatorRepository;
import com.ctip.domain.fingerprint.Sha256FingerprintStrategy;
import com.ctip.domain.indicator.Indicator;
import com.ctip.domain.indicator.IndicatorId;
import com.ctip.domain.indicator.IndicatorSourceSnapshot;
import com.ctip.domain.indicator.IocValue;
import com.ctip.domain.indicator.NewIndicatorCommand;
import com.ctip.domain.indicator.SourceRecordStatus;
import com.ctip.domain.source.Reputation;
import com.ctip.domain.source.SourceId;
import com.ctip.domain.tenant.TenantId;
import com.ctip.sdk.Confidence;
import com.ctip.sdk.IocType;
import com.ctip.sdk.RedistributionPolicy;
import com.ctip.sdk.Severity;
import com.ctip.sdk.Tlp;
import java.time.Instant;
import java.util.Set;

/**
 * 安全與隔離測試共用的 IOC fixture:固定 id、固定觀測時間、單一來源記錄。
 * 冪等——同一個 id 已存在即不動,整合測試共用同一個資料庫容器。
 */
public final class IndicatorFixtures {

    public static final Instant SEEN = Instant.parse("2026-08-10T00:00:00Z");

    private IndicatorFixtures() {}

    /** 一筆可控 owner / TLP / 再散布政策的 DOMAIN 型 IOC。 */
    public record Fixture(IndicatorId id, TenantId owner, Tlp tlp, RedistributionPolicy policy, String name) {}

    public static void upsert(IndicatorRepository indicators, SourceId sourceId, Fixture fixture) {
        if (indicators.findById(fixture.id()).isPresent()) {
            return;
        }
        String normalized = fixture.name() + ".security.ctip-sample.net";
        IndicatorSourceSnapshot report = new IndicatorSourceSnapshot(
                sourceId,
                normalized,
                Confidence.of(60),
                Severity.MEDIUM,
                fixture.tlp(),
                SEEN,
                SEEN,
                null,
                fixture.policy(),
                1,
                SourceRecordStatus.ACTIVE,
                Set.of("security-test"),
                java.util.Map.of());
        indicators.save(Indicator.create(
                new NewIndicatorCommand(
                        fixture.id(),
                        fixture.owner(),
                        new IocValue(IocType.DOMAIN, null, normalized, normalized),
                        report,
                        new Reputation(70)),
                new Sha256FingerprintStrategy()));
    }
}
