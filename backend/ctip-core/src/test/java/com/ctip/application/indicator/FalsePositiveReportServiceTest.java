package com.ctip.application.indicator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ctip.application.identity.AuthenticatedIdentity;
import com.ctip.application.rbac.RoleCode;
import com.ctip.domain.event.DomainEvent;
import com.ctip.domain.event.IndicatorEvents.IndicatorFalsePositiveReported;
import com.ctip.domain.fingerprint.Sha256FingerprintStrategy;
import com.ctip.domain.indicator.Indicator;
import com.ctip.domain.indicator.IndicatorId;
import com.ctip.domain.indicator.IndicatorSourceSnapshot;
import com.ctip.domain.indicator.IndicatorStatus;
import com.ctip.domain.indicator.IocValue;
import com.ctip.domain.indicator.NewIndicatorCommand;
import com.ctip.domain.indicator.SourceRecordStatus;
import com.ctip.domain.source.Reputation;
import com.ctip.domain.source.SourceId;
import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.user.UserId;
import com.ctip.sdk.IocType;
import com.ctip.sdk.RedistributionPolicy;
import com.ctip.sdk.Tlp;
import com.ctip.testing.FixedClockPort;
import com.ctip.testing.InMemoryIndicatorRepository;
import com.ctip.testing.ManualIngestionHarness;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * 誤判回報(docs/spec/09-api.md §9.7「誤判回報的作用域」;不變量 I11 規則 2)。
 *
 * <p>兩件事不可由呼叫端左右:最終狀態(由合併規則決定)與作用域(只接受自家 IOC)。
 */
@Tag("unit")
class FalsePositiveReportServiceTest {

    private static final TenantId TENANT = new TenantId(new UUID(0, 51));
    private static final UserId USER = new UserId(new UUID(0, 52));
    private static final SourceId FEED = new SourceId(new UUID(0, 53));

    private final FixedClockPort clock = FixedClockPort.at(FixedClockPort.DEFAULT_NOW);
    private final InMemoryIndicatorRepository indicators = new InMemoryIndicatorRepository();
    private final ManualIngestionHarness harness = new ManualIngestionHarness();
    private final List<DomainEvent> events = new ArrayList<>();
    private final FalsePositiveReportService service =
            new FalsePositiveReportService(indicators, harness.sources(), events::add, clock);

    private static AuthenticatedIdentity reporter(TenantId tenantId) {
        return AuthenticatedIdentity.ofUser(USER, tenantId, RoleCode.USER, Set.of("ioc:report-fp"));
    }

    private Indicator store(TenantId owner, IndicatorSourceSnapshot record) {
        String value = record.sourceValue();
        return indicators.save(Indicator.create(
                new NewIndicatorCommand(
                        new IndicatorId(new UUID(0, 60 + indicators.size())),
                        owner,
                        new IocValue(IocType.DOMAIN, null, value, value),
                        record,
                        new Reputation(70)),
                new Sha256FingerprintStrategy()));
    }

    private IndicatorSourceSnapshot record(
            String value, Tlp tlp, RedistributionPolicy policy, SourceRecordStatus status) {
        return new IndicatorSourceSnapshot(
                FEED, value, null, null, tlp, clock.now(), clock.now(), null, policy, 1, status, Set.of(), Map.of());
    }

    /** MANUAL 來源的記錄原本不存在 → 依 §9.7 建立,並由合併規則判成 FALSE_POSITIVE。 */
    @Test
    void missingManualRecordIsCreatedAndStatusFollowsTheMergePolicy() {
        // 唯一的既有來源已過期,MANUAL 的誤判因此成為唯一有效意見(I11 規則 2 的前提)
        Indicator stored = store(
                TENANT,
                record("fp.example.org", Tlp.AMBER, RedistributionPolicy.INTERNAL_ONLY, SourceRecordStatus.EXPIRED));

        Indicator reported =
                service.report(stored.id(), "cdn", null, reporter(TENANT)).orElseThrow();

        assertThat(reported.snapshot().sources()).anySatisfy(record -> {
            assertThat(record.sourceId()).isEqualTo(ManualIngestionHarness.MANUAL_SOURCE_ID);
            assertThat(record.status()).isEqualTo(SourceRecordStatus.FALSE_POSITIVE);
            assertThat(record.rawPayload()).containsEntry("falsePositiveReason", "cdn");
        });
        assertThat(reported.status()).isEqualTo(IndicatorStatus.FALSE_POSITIVE);
        assertThat(events).anyMatch(IndicatorFalsePositiveReported.class::isInstance);
    }

    /** 判準的核心:還有 ACTIVE 來源時,狀態仍是 ACTIVE——呼叫端不能指定結果。 */
    @Test
    void statusIsNotDictatedByTheCaller() {
        Indicator stored = store(
                TENANT,
                record(
                        "fp-active.example.org",
                        Tlp.AMBER,
                        RedistributionPolicy.ATTRIBUTION_REQUIRED,
                        SourceRecordStatus.ACTIVE));

        Indicator reported =
                service.report(stored.id(), "cdn", null, reporter(TENANT)).orElseThrow();

        assertThat(reported.status()).isEqualTo(IndicatorStatus.ACTIVE);
        assertThat(events).noneMatch(IndicatorFalsePositiveReported.class::isInstance);
    }

    /** 公開情資 → 403(對應例外);租戶自己的 IOC 才在本端點的作用域內。 */
    @Test
    void publicIntelligenceIsOutOfScope() {
        Indicator publicIoc = store(
                TenantId.PUBLIC,
                record(
                        "fp-public.example.org",
                        Tlp.CLEAR,
                        RedistributionPolicy.PUBLIC_REDISTRIBUTABLE,
                        SourceRecordStatus.ACTIVE));

        assertThatThrownBy(() -> service.report(publicIoc.id(), "cdn", null, reporter(TENANT)))
                .isInstanceOf(PublicIntelNotReportableException.class);
    }

    /** 查無或不可見 → empty(API 層回 404,不洩漏存在性)。 */
    @Test
    void unknownIndicatorYieldsEmpty() {
        assertThat(service.report(new IndicatorId(new UUID(0, 99)), "x", null, reporter(TENANT)))
                .isEmpty();
    }

    /** 新建的來源記錄沿用 Indicator 現值的 TLP:否則一次回報就把自家 CLEAR 資料變成 AMBER。 */
    @Test
    void createdRecordDoesNotTightenTheIndicatorTlp() {
        Indicator stored = store(
                TENANT,
                record(
                        "fp-clear.example.org",
                        Tlp.CLEAR,
                        RedistributionPolicy.PUBLIC_REDISTRIBUTABLE,
                        SourceRecordStatus.ACTIVE));

        Indicator reported =
                service.report(stored.id(), "cdn", null, reporter(TENANT)).orElseThrow();

        assertThat(reported.tlp()).isEqualTo(Tlp.CLEAR);
    }
}
