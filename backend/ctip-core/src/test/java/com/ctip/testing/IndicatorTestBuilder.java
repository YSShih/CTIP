package com.ctip.testing;

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
import java.util.UUID;

/** 測試 fixture(docs/spec/14-testing.md §14.7),僅供測試使用。時間一律固定值。 */
public final class IndicatorTestBuilder {

    public static final Instant T0 = Instant.parse("2026-08-01T00:00:00Z");
    public static final SourceId SOURCE_A = new SourceId(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    public static final SourceId SOURCE_B = new SourceId(UUID.fromString("00000000-0000-0000-0000-0000000000b2"));
    public static final SourceId SOURCE_C = new SourceId(UUID.fromString("00000000-0000-0000-0000-0000000000c3"));
    public static final TenantId DEMO_TENANT = new TenantId(UUID.fromString("00000000-0000-0000-0000-000000000001"));

    private IndicatorTestBuilder() {}

    public static IocValue domainValue(String normalized) {
        return new IocValue(IocType.DOMAIN, null, normalized, normalized);
    }

    public static ReportBuilder report(SourceId sourceId) {
        return new ReportBuilder(sourceId);
    }

    public static Indicator activeIndicator(TenantId owner, Tlp tlp, RedistributionPolicy policy) {
        IndicatorSourceSnapshot first = report(SOURCE_A).tlp(tlp).policy(policy).build();
        NewIndicatorCommand cmd = new NewIndicatorCommand(
                new IndicatorId(UUID.fromString("00000000-0000-0000-0000-00000000f00d")),
                owner,
                domainValue("mal-example.ctip-sample.net"),
                first,
                new Reputation(70));
        return Indicator.create(cmd, new Sha256FingerprintStrategy());
    }

    /** 可鏈式調整的來源記錄建構器(僅測試用,非 domain builder)。 */
    public static final class ReportBuilder {
        private final SourceId sourceId;
        private Confidence confidence = Confidence.of(60);
        private Severity severity = Severity.MEDIUM;
        private Tlp tlp = Tlp.CLEAR;
        private Instant firstSeen = T0;
        private Instant lastSeen = T0;
        private Instant validUntil;
        private RedistributionPolicy policy = RedistributionPolicy.PUBLIC_REDISTRIBUTABLE;
        private SourceRecordStatus status = SourceRecordStatus.ACTIVE;
        private Set<String> tags = Set.of();

        private ReportBuilder(SourceId sourceId) {
            this.sourceId = sourceId;
        }

        public ReportBuilder confidence(Confidence value) {
            this.confidence = value;
            return this;
        }

        public ReportBuilder severity(Severity value) {
            this.severity = value;
            return this;
        }

        public ReportBuilder tlp(Tlp value) {
            this.tlp = value;
            return this;
        }

        public ReportBuilder seen(Instant first, Instant last) {
            this.firstSeen = first;
            this.lastSeen = last;
            return this;
        }

        public ReportBuilder validUntil(Instant value) {
            this.validUntil = value;
            return this;
        }

        public ReportBuilder policy(RedistributionPolicy value) {
            this.policy = value;
            return this;
        }

        public ReportBuilder status(SourceRecordStatus value) {
            this.status = value;
            return this;
        }

        public ReportBuilder tags(Set<String> value) {
            this.tags = value;
            return this;
        }

        public IndicatorSourceSnapshot build() {
            return new IndicatorSourceSnapshot(
                    sourceId,
                    "mal-example.ctip-sample.net",
                    confidence,
                    severity,
                    tlp,
                    firstSeen,
                    lastSeen,
                    validUntil,
                    policy,
                    1,
                    status,
                    tags);
        }
    }
}
