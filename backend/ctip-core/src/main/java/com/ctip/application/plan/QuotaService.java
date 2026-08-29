package com.ctip.application.plan;

import com.ctip.application.port.ClockPort;
import com.ctip.application.port.PlanRepository;
import com.ctip.application.port.RateLimitKey;
import com.ctip.application.port.RateLimitResult;
import com.ctip.application.port.RateLimiterPort;
import com.ctip.application.port.SubscriptionRepository;
import com.ctip.domain.plan.Plan;
import com.ctip.domain.plan.PlanCode;
import com.ctip.domain.plan.QuotaLimit;
import com.ctip.domain.plan.Subscription;
import com.ctip.domain.tenant.TenantId;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 方案配額的單一判定點(docs/spec/10-identity-plans.md §10.6、09 §9.7)。
 *
 * <p><strong>不得在任何呼叫端 hard-code 配額數值</strong>——全部由 plans 表讀出。
 * 超限的三種語意各有對應例外(§9.7;ADR 0019):
 * <ul>
 *   <li>時間窗內的計數 → {@link QuotaExhaustedException} → 429</li>
 *   <li>非時間窗的能力上限 → {@link PlanLimitExceededException} → 403</li>
 *   <li>單次請求的尺寸上限 → {@link RequestSizeLimitExceededException} → 413</li>
 *   <li>單次分頁上限 → 夾到上限,不報錯</li>
 * </ul>
 *
 * <p>配額值 {@code 0} 代表<strong>停用</strong>(ADR 0019),不是「用完」:它不會隨視窗恢復,
 * 因此即使是每日計數型的配額,{@code 0} 也回 403 而非 429——回 429 + Retry-After
 * 等於告訴 client「等一下再試就會過」,而那永遠不會發生(ADR 0023)。
 */
@Service
public class QuotaService {

    private final PlanRepository plans;
    private final SubscriptionRepository subscriptions;
    private final RateLimiterPort rateLimiter;
    private final ClockPort clock;

    public QuotaService(
            PlanRepository plans, SubscriptionRepository subscriptions, RateLimiterPort rateLimiter, ClockPort clock) {
        this.plans = plans;
        this.subscriptions = subscriptions;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    /**
     * 某租戶此刻生效的方案(不變量 B4)。
     *
     * <p>public tenant 沒有也不得有訂閱(T3),匿名請求一律綁在它身上,故回 ANONYMOUS;
     * 已登入但沒有有效訂閱者視為 FREE。
     */
    @Transactional(readOnly = true)
    public Plan planFor(TenantId tenantId) {
        if (tenantId.isPublic()) {
            return byCode(PlanCode.ANONYMOUS);
        }
        return byCode(subscriptions
                .findActiveByTenant(tenantId)
                .map(subscription -> subscription.effectivePlanCode(clock.now()))
                .orElse(PlanCode.FREE));
    }

    @Transactional(readOnly = true)
    public Optional<Subscription> subscriptionOf(TenantId tenantId) {
        return tenantId.isPublic() ? Optional.empty() : subscriptions.findActiveByTenant(tenantId);
    }

    public Plan byCode(PlanCode code) {
        return plans.findByCode(code)
                .orElseThrow(() -> new IllegalStateException("plans 表缺少方案 " + code + ";V29 種子未套用?"));
    }

    /** §9.3:limit 超過方案上限夾到上限,不報錯。 */
    public int clampPageSize(TenantId tenantId, Integer requested, int fallback) {
        int max = planFor(tenantId).maxPageSize();
        if (requested == null) {
            return Math.min(fallback, max);
        }
        return Math.max(1, Math.min(requested, max));
    }

    /** 批次精確驗證的單次上限(§9.7 → 413)。 */
    public void requireBatchLookupWithin(TenantId tenantId, int size) {
        QuotaLimit limit = planFor(tenantId).maxBatchLookup();
        if (limit.isDisabled()) {
            throw new PlanLimitExceededException("Batch lookup is not available on this plan");
        }
        if (limit.isExceededBy(size)) {
            throw new RequestSizeLimitExceededException("Batch lookup exceeds limit of " + limit.orElse(size));
        }
    }

    /** 單檔匯入筆數上限(§9.7 → 413;0 = 該方案不允許匯入 → 403)。 */
    public void requireImportRowsWithin(TenantId tenantId, int rows) {
        QuotaLimit limit = planFor(tenantId).maxImportRowsPerFile();
        if (limit.isDisabled()) {
            throw new PlanLimitExceededException("Bulk import is not available on this plan");
        }
        if (limit.isExceededBy(rows)) {
            throw new RequestSizeLimitExceededException("Import exceeds limit of " + limit.orElse(rows) + " rows");
        }
    }

    /** STIX bundle 的物件數上限(§9.7 → 403,非時間窗)。 */
    public QuotaLimit stixExportLimit(TenantId tenantId) {
        return planFor(tenantId).stixExportMaxObjects();
    }

    /** API key 數量上限(§9.7 → 403,非時間窗)。 */
    public void requireApiKeyHeadroom(TenantId tenantId, long activeCount) {
        QuotaLimit limit = planFor(tenantId).maxApiKeys();
        if (limit.isDisabled()) {
            throw new PlanLimitExceededException("API keys are not available on this plan");
        }
        if (limit.isExceededBy(activeCount + 1)) {
            throw new PlanLimitExceededException("API key quota exhausted");
        }
    }

    /** Webhook 數量上限(不變量 W6;§9.7 → 403,非時間窗)。 */
    public void requireWebhookHeadroom(TenantId tenantId, long existingCount) {
        QuotaLimit limit = planFor(tenantId).maxWebhooks();
        if (limit.isDisabled()) {
            throw new PlanLimitExceededException("Webhooks are not available on this plan");
        }
        if (limit.isExceededBy(existingCount + 1)) {
            throw new PlanLimitExceededException("Webhook quota exhausted");
        }
    }

    /**
     * 即時推送的方案閘門(§10.6 {@code websocket_enabled};09 §9.1「即時推送」的授權列)。
     *
     * <p>SSE fallback 一併受此閘門管制:它與 WebSocket 是同一個能力的兩種傳輸,
     * 只擋 WebSocket 等於任何 client 改用 {@code /events} 就繞過方案限制。
     */
    public void requireRealtimePush(TenantId tenantId) {
        if (!planFor(tenantId).websocketEnabled()) {
            throw new PlanLimitExceededException("Realtime push is not available on this plan");
        }
    }

    /**
     * 扣減手動提交的每日配額(§10.6 {@code max_manual_submissions_per_day})。
     *
     * <p>這是<strong>唯一</strong>阻止「自助註冊即免費取得 PREMIUM 提交能力」的閘門:
     * 自助註冊者拿到 TENANT_ADMIN 角色,而該角色持有 {@code ioc:submit}(ADR 0012 決策 5),
     * FREE 方案的每日上限 0 必須真的被檢查。
     */
    public RateLimitResult consumeManualSubmissions(TenantId tenantId, int count) {
        QuotaLimit limit = planFor(tenantId).maxManualSubmissionsPerDay();
        if (limit.isDisabled()) {
            throw new PlanLimitExceededException("Manual submission is not available on this plan");
        }
        RateLimitResult result = rateLimiter.tryConsume(RateLimitKey.manualSubmissions(tenantId.value()), count, limit);
        if (!result.allowed()) {
            throw new QuotaExhaustedException("Daily manual submission quota exhausted", result);
        }
        return result;
    }

    /** 用量查詢:不消耗配額。 */
    public RateLimitResult manualSubmissionUsage(TenantId tenantId) {
        QuotaLimit limit = planFor(tenantId).maxManualSubmissionsPerDay();
        return rateLimiter.peek(RateLimitKey.manualSubmissions(tenantId.value()), limit);
    }

    /**
     * 匯入路徑的事後扣減:批次已成功寫入,越界的筆數在 pipeline 內就已逐筆記為
     * {@code QUOTA_EXCEEDED}(§9.7「已接受的部分不該因為後半超額而整批失敗」),
     * 故此處只記帳、不丟例外。回傳是否全數計入(false 代表併發下有短少,只影響計數不影響資料)。
     */
    public boolean recordManualSubmissions(TenantId tenantId, int count) {
        if (count <= 0) {
            return true;
        }
        QuotaLimit limit = planFor(tenantId).maxManualSubmissionsPerDay();
        return rateLimiter
                .tryConsume(RateLimitKey.manualSubmissions(tenantId.value()), count, limit)
                .allowed();
    }
}
