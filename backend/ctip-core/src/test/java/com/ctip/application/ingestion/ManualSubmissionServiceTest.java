package com.ctip.application.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ctip.application.identity.AuthenticatedIdentity;
import com.ctip.application.plan.PlanLimitExceededException;
import com.ctip.application.plan.QuotaExhaustedException;
import com.ctip.application.plan.QuotaService;
import com.ctip.application.rbac.RoleCode;
import com.ctip.domain.plan.BillingPeriod;
import com.ctip.domain.plan.PlanCode;
import com.ctip.domain.plan.Subscription;
import com.ctip.domain.plan.SubscriptionId;
import com.ctip.domain.plan.SubscriptionProvider;
import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.user.UserId;
import com.ctip.sdk.IocType;
import com.ctip.sdk.Severity;
import com.ctip.sdk.Tlp;
import com.ctip.testing.CountingRateLimiter;
import com.ctip.testing.FixedClockPort;
import com.ctip.testing.InMemoryPlanRepository;
import com.ctip.testing.InMemorySubscriptionRepository;
import com.ctip.testing.ManualIngestionHarness;
import com.ctip.testing.PlanFixtures;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * 單筆手動提交(docs/spec/09-api.md §9.7、08 §8.3)。
 *
 * <p>刻意跑<strong>真的 pipeline</strong>({@link ManualIngestionHarness}):§8.3 的重點就是
 * 「複用同一條 pipeline,不需要第二套資料品質邏輯」,用假的 executor 測等於沒測到那件事。
 */
@Tag("unit")
class ManualSubmissionServiceTest {

    private static final TenantId TENANT = new TenantId(new UUID(0, 31));
    private static final UserId USER = new UserId(new UUID(0, 32));

    private final FixedClockPort clock = FixedClockPort.at(FixedClockPort.DEFAULT_NOW);
    private final ManualIngestionHarness harness = new ManualIngestionHarness();
    private final InMemoryPlanRepository plans = new InMemoryPlanRepository();
    private final InMemorySubscriptionRepository subscriptions = new InMemorySubscriptionRepository();
    private final QuotaService quotas = new QuotaService(plans, subscriptions, new CountingRateLimiter(clock), clock);
    private final ManualSubmissionService service =
            new ManualSubmissionService(harness.executor(), harness.sources(), quotas, clock);

    private AuthenticatedIdentity submitter(String... permissions) {
        return AuthenticatedIdentity.ofUser(USER, TENANT, RoleCode.TENANT_ADMIN, Set.of(permissions));
    }

    private void subscribePremium() {
        subscriptions.save(Subscription.subscribe(
                new SubscriptionId(new UUID(0, 33)),
                TENANT,
                PlanFixtures.of(PlanCode.PREMIUM),
                SubscriptionProvider.MANUAL,
                BillingPeriod.openEnded(clock.now())));
    }

    private static ManualSubmissionCommand command(String value, Tlp tlp) {
        return new ManualSubmissionCommand(
                IocType.DOMAIN, value, null, 70, Severity.HIGH, tlp, null, Set.of("manual"), "incident 42");
    }

    @Test
    void defaultsToAmberAndOwnsTheSubmitterTenant() {
        subscribePremium();

        RecordOutcome outcome = service.submit(command("manual-default.example.org", null), submitter("ioc:submit"));

        assertThat(outcome.rejected()).isFalse();
        assertThat(outcome.indicator().tlp()).isEqualTo(Tlp.AMBER);
        assertThat(outcome.indicator().ownerTenantId()).isEqualTo(TENANT);
        // note 落 raw_payload,不另開欄位
        assertThat(outcome.indicator().snapshot().sources())
                .singleElement()
                .satisfies(record -> assertThat(record.rawPayload()).containsEntry("note", "incident 42"));
    }

    /** 複用 pipeline:同值再次提交命中去重 → merged,而不是第二筆 Indicator。 */
    @Test
    void resubmissionIsMergedByTheSharedPipeline() {
        subscribePremium();
        RecordOutcome first = service.submit(command("manual-dedup.example.org", null), submitter("ioc:submit"));

        RecordOutcome second = service.submit(command("Manual-Dedup.Example.ORG.", null), submitter("ioc:submit"));

        assertThat(second.merged()).isTrue();
        assertThat(second.indicator().id()).isEqualTo(first.indicator().id());
    }

    /** 複用 pipeline:私有 IP 一樣被拒(不是手動提交就放行)。 */
    @Test
    void pipelineRejectionsAreReportedNotSilentlyAccepted() {
        subscribePremium();
        ManualSubmissionCommand privateIp =
                new ManualSubmissionCommand(IocType.IPV4, "10.1.2.3", null, null, null, null, null, Set.of(), null);

        RecordOutcome outcome = service.submit(privateIp, submitter("ioc:submit"));

        assertThat(outcome.rejected()).isTrue();
        assertThat(outcome.rejectionReason()).isEqualTo(RejectionReason.PRIVATE_OR_RESERVED_IP);
        assertThat(harness.rejections()).hasSize(1);
    }

    @Test
    void publishingRequiresTheIocPublishPermission() {
        subscribePremium();

        assertThatThrownBy(
                        () -> service.submit(command("manual-publish.example.org", Tlp.CLEAR), submitter("ioc:submit")))
                .isInstanceOf(PublishNotPermittedException.class);
    }

    /**
     * {@code ioc:publish} = 擁有權轉移(ADR 0019 第 2 節),且來源記錄必須可再散布——
     * 兩者缺一,發布出去的 IOC 就沒有任何人看得到(ADR 0023)。
     */
    @Test
    void publishingTransfersOwnershipAndMakesItRedistributable() {
        subscribePremium();

        RecordOutcome outcome = service.submit(
                command("manual-published.example.org", Tlp.CLEAR), submitter("ioc:submit", "ioc:publish"));

        assertThat(outcome.indicator().ownerTenantId()).isEqualTo(TenantId.PUBLIC);
        assertThat(outcome.indicator().tlp()).isEqualTo(Tlp.CLEAR);
        assertThat(outcome.indicator().canBeRedistributedTo(TenantId.PUBLIC)).isTrue();
        assertThat(outcome.indicator().eligibleForBloom()).isTrue();
    }

    @Test
    void redIsRejectedBeforeTouchingThePipeline() {
        subscribePremium();

        assertThatThrownBy(() -> service.submit(command("manual-red.example.org", Tlp.RED), submitter("ioc:submit")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(harness.indicators().size()).isZero();
    }

    /** FREE 的每日提交上限是 0 = 停用 → 403,而且必須在寫入之前就擋下。 */
    @Test
    void freePlanCannotSubmitAndNothingIsPersisted() {
        assertThatThrownBy(() -> service.submit(command("manual-free.example.org", null), submitter("ioc:submit")))
                .isInstanceOf(PlanLimitExceededException.class);
        assertThat(harness.indicators().size()).isZero();
    }

    @Test
    void dailyQuotaIsConsumedPerSubmission() {
        subscribePremium();
        plans.put(com.ctip.testing.PlanQuotas.manualSubmissionsPerDay(PlanFixtures.of(PlanCode.PREMIUM), 1));

        service.submit(command("manual-quota-1.example.org", null), submitter("ioc:submit"));

        assertThatThrownBy(() -> service.submit(command("manual-quota-2.example.org", null), submitter("ioc:submit")))
                .isInstanceOf(QuotaExhaustedException.class);
    }
}
