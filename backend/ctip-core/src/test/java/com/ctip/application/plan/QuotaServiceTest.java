package com.ctip.application.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ctip.domain.plan.BillingPeriod;
import com.ctip.domain.plan.PlanCode;
import com.ctip.domain.plan.Subscription;
import com.ctip.domain.plan.SubscriptionId;
import com.ctip.domain.plan.SubscriptionProvider;
import com.ctip.domain.tenant.TenantId;
import com.ctip.testing.CountingRateLimiter;
import com.ctip.testing.FixedClockPort;
import com.ctip.testing.InMemoryPlanRepository;
import com.ctip.testing.InMemorySubscriptionRepository;
import com.ctip.testing.PlanFixtures;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * 配額判定(docs/spec/09-api.md §9.7「配額超限的三種語意」、10 §10.6)。
 *
 * <p>三種語意各有出口:429(時間窗)、403(能力上限)、413(單次尺寸);
 * 分頁上限則夾值不報錯。此外 B4:沒有訂閱的租戶是 FREE、public tenant 是 ANONYMOUS。
 */
@Tag("unit")
class QuotaServiceTest {

    private static final TenantId TENANT = new TenantId(new UUID(0, 21));

    private final FixedClockPort clock = FixedClockPort.at(FixedClockPort.DEFAULT_NOW);
    private final InMemoryPlanRepository plans = new InMemoryPlanRepository();
    private final InMemorySubscriptionRepository subscriptions = new InMemorySubscriptionRepository();
    private final QuotaService quotas = new QuotaService(plans, subscriptions, new CountingRateLimiter(clock), clock);

    private void subscribe(PlanCode code) {
        subscriptions.save(Subscription.subscribe(
                new SubscriptionId(new UUID(0, 99)),
                TENANT,
                PlanFixtures.of(code),
                SubscriptionProvider.MANUAL,
                BillingPeriod.openEnded(clock.now())));
    }

    @Test
    void b4TenantWithoutSubscriptionIsFreeAndPublicTenantIsAnonymous() {
        assertThat(quotas.planFor(TENANT).code()).isEqualTo(PlanCode.FREE);
        assertThat(quotas.planFor(TenantId.PUBLIC).code()).isEqualTo(PlanCode.ANONYMOUS);
    }

    @Test
    void pageSizeIsClampedNotRejected() {
        assertThat(quotas.clampPageSize(TenantId.PUBLIC, 5000, 50)).isEqualTo(50);
        assertThat(quotas.clampPageSize(TENANT, 5000, 50)).isEqualTo(100);
        assertThat(quotas.clampPageSize(TENANT, null, 50)).isEqualTo(50);
    }

    /** 單次尺寸上限 → 413(§9.7)。 */
    @Test
    void batchLookupBeyondLimitIsASizeError() {
        assertThatThrownBy(() -> quotas.requireBatchLookupWithin(TenantId.PUBLIC, 21))
                .isInstanceOf(RequestSizeLimitExceededException.class);
        quotas.requireBatchLookupWithin(TenantId.PUBLIC, 20);
    }

    /** 方案未開放的能力(0 = 停用)→ 403,而不是 429:等待不會讓它恢復。 */
    @Test
    void disabledCapabilityIsAPlanLimitNotARateLimit() {
        assertThatThrownBy(() -> quotas.consumeManualSubmissions(TENANT, 1))
                .isInstanceOf(PlanLimitExceededException.class);
        assertThatThrownBy(() -> quotas.requireImportRowsWithin(TENANT, 1))
                .isInstanceOf(PlanLimitExceededException.class);
    }

    /** 時間窗內的計數用罄 → 429,且帶得出重置時間。 */
    @Test
    void dailySubmissionQuotaExhaustionIsARateLimit() {
        subscribe(PlanCode.PREMIUM);

        quotas.consumeManualSubmissions(TENANT, 999);
        quotas.consumeManualSubmissions(TENANT, 1);

        assertThatThrownBy(() -> quotas.consumeManualSubmissions(TENANT, 1))
                .isInstanceOfSatisfying(QuotaExhaustedException.class, e -> {
                    assertThat(e.result().allowed()).isFalse();
                    assertThat(e.result().resetAt()).isAfter(clock.now());
                });
    }

    @Test
    void apiKeyHeadroomUsesThePlanLimit() {
        quotas.requireApiKeyHeadroom(TENANT, 0); // FREE = 1
        assertThatThrownBy(() -> quotas.requireApiKeyHeadroom(TENANT, 1))
                .isInstanceOf(PlanLimitExceededException.class);

        subscribe(PlanCode.PREMIUM); // = 10
        quotas.requireApiKeyHeadroom(TENANT, 9);
        assertThatThrownBy(() -> quotas.requireApiKeyHeadroom(TENANT, 10))
                .isInstanceOf(PlanLimitExceededException.class);
    }

    /** 用量查詢不得消耗配額——否則打開用量頁面就把配額用掉了。 */
    @Test
    void usageQueryDoesNotConsume() {
        subscribe(PlanCode.PREMIUM);
        quotas.consumeManualSubmissions(TENANT, 3);

        assertThat(quotas.manualSubmissionUsage(TENANT).used()).isEqualTo(3);
        assertThat(quotas.manualSubmissionUsage(TENANT).used()).isEqualTo(3);
    }

    /** ENTERPRISE 的無限制不得被當成 0(否則最高階方案反而完全不能用)。 */
    @Test
    void unlimitedPlanValuesAreNotTreatedAsDisabled() {
        subscribe(PlanCode.ENTERPRISE);

        assertThat(quotas.stixExportLimit(TENANT).isUnlimited()).isTrue();
        assertThat(quotas.manualSubmissionUsage(TENANT).limit().orElse(0)).isEqualTo(50_000);
    }
}
