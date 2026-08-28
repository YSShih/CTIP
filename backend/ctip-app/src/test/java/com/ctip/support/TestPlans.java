package com.ctip.support;

import com.ctip.application.port.ClockPort;
import com.ctip.application.port.IdGeneratorPort;
import com.ctip.application.port.PlanRepository;
import com.ctip.application.port.SubscriptionRepository;
import com.ctip.domain.plan.BillingPeriod;
import com.ctip.domain.plan.Plan;
import com.ctip.domain.plan.PlanCode;
import com.ctip.domain.plan.QuotaLimit;
import com.ctip.domain.plan.Subscription;
import com.ctip.domain.plan.SubscriptionId;
import com.ctip.domain.plan.SubscriptionProvider;
import com.ctip.domain.tenant.TenantId;
import java.util.function.UnaryOperator;

/**
 * 整合測試的方案／訂閱操作。
 *
 * <p>M2 沒有「指派方案」的端點(§10.6:由 SYSTEM_ADMIN 手動操作,管理端點是 M3),
 * 因此測試直接經 port 寫入——這是<strong>唯一</strong>沒有真實入口的路徑,
 * 其餘一律走端點(見 {@link TestIdentities})。
 */
public final class TestPlans {

    private final PlanRepository plans;
    private final SubscriptionRepository subscriptions;
    private final IdGeneratorPort idGenerator;
    private final ClockPort clock;

    public TestPlans(
            PlanRepository plans, SubscriptionRepository subscriptions, IdGeneratorPort idGenerator, ClockPort clock) {
        this.plans = plans;
        this.subscriptions = subscriptions;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    /** 給租戶一份 ACTIVE 訂閱;沒有訂閱的租戶視為 FREE(不變量 B4)。 */
    public Subscription assign(TenantId tenantId, PlanCode code) {
        Plan plan = plans.findByCode(code).orElseThrow();
        return subscriptions.save(Subscription.subscribe(
                new SubscriptionId(idGenerator.nextId()),
                tenantId,
                plan,
                SubscriptionProvider.MANUAL,
                BillingPeriod.openEnded(clock.now())));
    }

    /**
     * 暫時改寫某方案的配額後執行 body,結束一律還原。
     *
     * <p>plans 是全域參考資料而整合測試共用同一個 context——不還原就會讓後續測試看到被改過的配額,
     * 那種失敗極難追。還原走 finally,body 丟例外也會執行。
     */
    public void withPlan(PlanCode code, UnaryOperator<Plan> change, ThrowingRunnable body) throws Exception {
        Plan original = plans.findByCode(code).orElseThrow();
        plans.save(change.apply(original));
        try {
            body.run();
        } finally {
            plans.save(original);
        }
    }

    /** 只改每日手動提交上限的常見情境。 */
    public static UnaryOperator<Plan> manualSubmissionsPerDay(long value) {
        return plan -> new Plan(
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
                QuotaLimit.of(value),
                plan.maxImportRowsPerFile());
    }

    /** 只改最小同步間隔(11 §11.6;0 = 不限制)。 */
    public static UnaryOperator<Plan> minSyncIntervalSeconds(int value) {
        return plan -> new Plan(
                plan.id(),
                plan.code(),
                plan.name(),
                plan.tier(),
                plan.requestsPerMinute(),
                plan.requestsPerDay(),
                plan.maxPageSize(),
                plan.maxBatchLookup(),
                value,
                plan.publicBloomEnabled(),
                plan.tenantBloomCapacity(),
                plan.websocketEnabled(),
                plan.maxWebhooks(),
                plan.maxApiKeys(),
                plan.customFeedEnabled(),
                plan.stixExportMaxObjects(),
                plan.maxManualSubmissionsPerDay(),
                plan.maxImportRowsPerFile());
    }

    /** 只改「是否可下載 public Bloom」(11 §11.5 的下載授權)。 */
    public static UnaryOperator<Plan> publicBloomEnabled(boolean value) {
        return plan -> new Plan(
                plan.id(),
                plan.code(),
                plan.name(),
                plan.tier(),
                plan.requestsPerMinute(),
                plan.requestsPerDay(),
                plan.maxPageSize(),
                plan.maxBatchLookup(),
                plan.minSyncIntervalSeconds(),
                value,
                plan.tenantBloomCapacity(),
                plan.websocketEnabled(),
                plan.maxWebhooks(),
                plan.maxApiKeys(),
                plan.customFeedEnabled(),
                plan.stixExportMaxObjects(),
                plan.maxManualSubmissionsPerDay(),
                plan.maxImportRowsPerFile());
    }

    /** 只改每分鐘請求上限(限流測試用;真實值 60 打起來太慢)。 */
    public static UnaryOperator<Plan> requestsPerMinute(long value) {
        return plan -> new Plan(
                plan.id(),
                plan.code(),
                plan.name(),
                plan.tier(),
                QuotaLimit.of(value),
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
                plan.maxImportRowsPerFile());
    }

    /** 直接改寫某方案(呼叫端自行還原)。 */
    public void save(Plan plan) {
        plans.save(plan);
    }

    public Plan plan(PlanCode code) {
        return plans.findByCode(code).orElseThrow();
    }

    /** 只改單檔匯入筆數上限。 */
    public static UnaryOperator<Plan> importRowsPerFile(long value) {
        return plan -> new Plan(
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
                QuotaLimit.of(value));
    }

    /** 只改 STIX bundle 的物件數上限。 */
    public static UnaryOperator<Plan> stixExportMaxObjects(long value) {
        return plan -> new Plan(
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
                QuotaLimit.of(value),
                plan.maxManualSubmissionsPerDay(),
                plan.maxImportRowsPerFile());
    }

    /** JUnit 的 Executable 等價物,讓 withPlan 的 body 可以丟受檢例外。 */
    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }
}
