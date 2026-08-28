package com.ctip.application.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ctip.application.identity.AuthenticatedIdentity;
import com.ctip.application.plan.PlanLimitExceededException;
import com.ctip.application.plan.QuotaService;
import com.ctip.application.plan.RequestSizeLimitExceededException;
import com.ctip.application.port.ImportPayloadParserPort;
import com.ctip.application.rbac.RoleCode;
import com.ctip.domain.plan.BillingPeriod;
import com.ctip.domain.plan.PlanCode;
import com.ctip.domain.plan.Subscription;
import com.ctip.domain.plan.SubscriptionId;
import com.ctip.domain.plan.SubscriptionProvider;
import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.user.UserId;
import com.ctip.sdk.IocType;
import com.ctip.sdk.RawThreatRecord;
import com.ctip.sdk.Tlp;
import com.ctip.testing.CountingRateLimiter;
import com.ctip.testing.FixedClockPort;
import com.ctip.testing.InMemoryImportJobRepository;
import com.ctip.testing.InMemoryPlanRepository;
import com.ctip.testing.InMemorySubscriptionRepository;
import com.ctip.testing.ManualIngestionHarness;
import com.ctip.testing.PlanFixtures;
import com.ctip.testing.PlanQuotas;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * 批次匯入(docs/spec/09-api.md §9.7)。
 *
 * <p>{@code @Async} 在單元測試裡沒有 proxy,{@link ImportJobRunner#run} 同步執行——
 * 正好讓 job 的狀態轉換與逐筆配額行為可被確定性地斷言。
 */
@Tag("unit")
class ImportServiceTest {

    private static final TenantId TENANT = new TenantId(new UUID(0, 41));
    private static final UserId USER = new UserId(new UUID(0, 42));

    private final FixedClockPort clock = FixedClockPort.at(FixedClockPort.DEFAULT_NOW);
    private final ManualIngestionHarness harness = new ManualIngestionHarness();
    private final InMemoryPlanRepository plans = new InMemoryPlanRepository();
    private final InMemorySubscriptionRepository subscriptions = new InMemorySubscriptionRepository();
    private final InMemoryImportJobRepository jobs = new InMemoryImportJobRepository();
    private final QuotaService quotas = new QuotaService(plans, subscriptions, new CountingRateLimiter(clock), clock);
    private final ManualSubmissionService submissions =
            new ManualSubmissionService(harness.executor(), harness.sources(), quotas, clock);
    private final ImportJobRunner runner = new ImportJobRunner(harness.executor(), submissions, jobs, quotas, clock);
    private final AtomicLong sequence = new AtomicLong(100);

    private ImportService serviceParsing(List<RawThreatRecord> records) {
        ImportPayloadParserPort parser = (format, payload) -> records;
        return new ImportService(
                parser,
                jobs,
                runner,
                quotas,
                new ImportJobFactory(() -> new UUID(0, sequence.getAndIncrement()), clock));
    }

    private static AuthenticatedIdentity importer() {
        return AuthenticatedIdentity.ofUser(USER, TENANT, RoleCode.TENANT_ADMIN, Set.of("ioc:import"));
    }

    private void subscribePremium() {
        subscriptions.save(Subscription.subscribe(
                new SubscriptionId(new UUID(0, 43)),
                TENANT,
                PlanFixtures.of(PlanCode.PREMIUM),
                SubscriptionProvider.MANUAL,
                BillingPeriod.openEnded(clock.now())));
    }

    private static List<RawThreatRecord> domains(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> new RawThreatRecord(
                        "import-" + i + ".example.org",
                        IocType.DOMAIN,
                        null,
                        FixedClockPort.DEFAULT_NOW,
                        null,
                        null,
                        null,
                        Set.of("bulk"),
                        Map.of()))
                .toList();
    }

    @Test
    void importedIocsArePrivateToTheTenant() {
        subscribePremium();

        ImportJob job = serviceParsing(domains(3)).submit(ImportFormat.CSV, "ignored", importer());

        ImportJob finished = jobs.find(job.id(), TENANT).orElseThrow();
        assertThat(finished.status()).isEqualTo(ImportJobStatus.SUCCESS);
        assertThat(finished.totalRows()).isEqualTo(3);
        assertThat(finished.acceptedCount()).isEqualTo(3);
        assertThat(harness.indicators().all()).allSatisfy(indicator -> {
            assertThat(indicator.ownerTenantId()).isEqualTo(TENANT);
            assertThat(indicator.tlp()).isEqualTo(Tlp.AMBER);
        });
    }

    /** 有拒絕筆數即 PARTIAL——請求本身仍成功,已接受的部分不因為有壞資料而整批失敗(§9.7)。 */
    @Test
    void rejectedRowsMakeTheJobPartialNotFailed() {
        subscribePremium();
        List<RawThreatRecord> mixed = new java.util.ArrayList<>(domains(2));
        mixed.add(new RawThreatRecord(
                "10.0.0.7", IocType.IPV4, null, FixedClockPort.DEFAULT_NOW, null, null, null, Set.of(), Map.of()));

        ImportJob job = serviceParsing(mixed).submit(ImportFormat.CSV, "ignored", importer());

        ImportJob finished = jobs.find(job.id(), TENANT).orElseThrow();
        assertThat(finished.status()).isEqualTo(ImportJobStatus.PARTIAL);
        assertThat(finished.acceptedCount()).isEqualTo(2);
        assertThat(finished.rejectedCount()).isEqualTo(1);
        assertThat(harness.rejections())
                .singleElement()
                .satisfies(rejected ->
                        assertThat(rejected.importJobId()).isEqualTo(job.id().value()));
    }

    /**
     * 中途跨越每日配額:越界的記錄逐筆記為 {@code QUOTA_EXCEEDED},
     * 已接受的部分保留(§9.7「已接受的部分不該因為後半超額而整批失敗」)。
     */
    @Test
    void rowsBeyondTheDailyQuotaAreRejectedIndividually() {
        subscribePremium();
        plans.put(PlanQuotas.manualSubmissionsPerDay(PlanFixtures.of(PlanCode.PREMIUM), 2));

        ImportJob job = serviceParsing(domains(5)).submit(ImportFormat.CSV, "ignored", importer());

        ImportJob finished = jobs.find(job.id(), TENANT).orElseThrow();
        assertThat(finished.acceptedCount()).isEqualTo(2);
        assertThat(finished.rejectedCount()).isEqualTo(3);
        assertThat(harness.rejections())
                .allSatisfy(rejected -> assertThat(rejected.reason()).isEqualTo(RejectionReason.QUOTA_EXCEEDED));
    }

    /** 單檔筆數上限 → 413,且不建立 job(沒有處理過的東西就不該有進度可查)。 */
    @Test
    void beyondThePlanRowLimitIsASizeErrorAndNoJobIsCreated() {
        subscribePremium();
        plans.put(com.ctip.testing.PlanQuotas.manualSubmissionsPerDay(PlanFixtures.of(PlanCode.PREMIUM), 1000));
        ImportService service = serviceParsing(domains(3));

        plans.put(importRowsPerFile(PlanFixtures.of(PlanCode.PREMIUM), 2));

        assertThatThrownBy(() -> service.submit(ImportFormat.CSV, "ignored", importer()))
                .isInstanceOf(RequestSizeLimitExceededException.class);
        assertThat(jobs.size()).isZero();
    }

    /** FREE 的 max_import_rows_per_file = 0 是「停用」→ 403,不是 413。 */
    @Test
    void freePlanCannotImport() {
        assertThatThrownBy(() -> serviceParsing(domains(1)).submit(ImportFormat.CSV, "ignored", importer()))
                .isInstanceOf(PlanLimitExceededException.class);
        assertThat(jobs.size()).isZero();
    }

    /** 跨租戶的 job 一律查無(§9.4:404,不回 403)。 */
    @Test
    void jobsAreScopedToTheirTenant() {
        subscribePremium();
        ImportJob job = serviceParsing(domains(1)).submit(ImportFormat.CSV, "ignored", importer());

        assertThat(jobs.find(job.id(), new TenantId(new UUID(0, 44)))).isEmpty();
    }

    private static com.ctip.domain.plan.Plan importRowsPerFile(com.ctip.domain.plan.Plan plan, long value) {
        return new com.ctip.domain.plan.Plan(
                plan.id(),
                plan.code(),
                plan.name(),
                plan.tier(),
                plan.requestsPerMinute(),
                plan.requestsPerDay(),
                plan.maxPageSize(),
                plan.maxBatchLookup(),
                plan.minSyncIntervalSeconds(),
                plan.publicBloomEnabled(),
                plan.tenantBloomCapacity(),
                plan.websocketEnabled(),
                plan.maxWebhooks(),
                plan.maxApiKeys(),
                plan.customFeedEnabled(),
                plan.stixExportMaxObjects(),
                plan.maxManualSubmissionsPerDay(),
                com.ctip.domain.plan.QuotaLimit.of(value));
    }
}
