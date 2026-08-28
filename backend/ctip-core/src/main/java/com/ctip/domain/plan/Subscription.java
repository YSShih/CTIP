package com.ctip.domain.plan;

import com.ctip.domain.event.DomainEvent;
import com.ctip.domain.event.PendingEvents;
import com.ctip.domain.event.SubscriptionEvents.SubscriptionChanged;
import com.ctip.domain.tenant.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 某租戶對某方案的有效關聯,聚合根(docs/spec/02-ddd-model.md,不變量 B1–B5)。
 *
 * <p>B1(一個 tenant 同時最多一份 ACTIVE)由 {@code ux_subscriptions_active} 部分唯一索引強制——
 * 應用層的檢查擋不住併發;B2 在 {@link BillingPeriod};B3–B5 在本型別。
 *
 * <p>跨聚合只以 ID 參照:本聚合持有 {@link PlanId} 與 {@link PlanCode}(方案是參考資料,
 * 其 code 是值不是實體參照),不持有 {@link Plan} 物件。
 */
public final class Subscription {

    private final SubscriptionId id;
    private final TenantId tenantId;
    private PlanId planId;
    private PlanCode planCode;
    private SubscriptionStatus status;
    private final SubscriptionProvider provider;
    private final String externalSubscriptionId;
    private BillingPeriod period;
    private Instant cancelledAt;
    private final PendingEvents pendingEvents = new PendingEvents();

    private Subscription(SubscriptionSnapshot snapshot, PlanCode planCode) {
        this.id = Objects.requireNonNull(snapshot.id(), "id 不得為 null");
        this.tenantId = Objects.requireNonNull(snapshot.tenantId(), "tenantId 不得為 null");
        this.planId = Objects.requireNonNull(snapshot.planId(), "planId 不得為 null");
        this.planCode = Objects.requireNonNull(planCode, "planCode 不得為 null");
        this.status = Objects.requireNonNull(snapshot.status(), "status 不得為 null");
        this.provider = Objects.requireNonNull(snapshot.provider(), "provider 不得為 null");
        this.externalSubscriptionId = snapshot.externalSubscriptionId();
        this.period = Objects.requireNonNull(snapshot.period(), "period 不得為 null");
        this.cancelledAt = snapshot.cancelledAt();
        if (tenantId.isPublic()) {
            throw new IllegalArgumentException("public tenant 不得有訂閱(不變量 T3)");
        }
        if (provider == SubscriptionProvider.NONE && externalSubscriptionId != null) {
            throw new IllegalArgumentException("provider = NONE 時 externalSubscriptionId 必須為 null(不變量 B5)");
        }
        if (status == SubscriptionStatus.CANCELLED && cancelledAt == null) {
            throw new IllegalArgumentException("CANCELLED 訂閱必須有 cancelledAt");
        }
    }

    /** 新訂閱一律以 ACTIVE 建立;M2 由 SYSTEM_ADMIN 手動指派(provider = MANUAL)。 */
    public static Subscription subscribe(
            SubscriptionId id, TenantId tenantId, Plan plan, SubscriptionProvider provider, BillingPeriod period) {
        Subscription subscription = new Subscription(
                new SubscriptionSnapshot(
                        id, tenantId, plan.id(), SubscriptionStatus.ACTIVE, provider, null, period, null),
                plan.code());
        subscription.pendingEvents.record(
                new SubscriptionChanged(tenantId, id, plan.code(), plan.code(), SubscriptionStatus.ACTIVE));
        return subscription;
    }

    public static Subscription reconstitute(SubscriptionSnapshot snapshot, PlanCode planCode) {
        return new Subscription(snapshot, planCode);
    }

    /** B3:CANCELLED 之後不可回到 ACTIVE——換方案必須建立新訂閱。 */
    public void changePlan(Plan plan, BillingPeriod newPeriod) {
        if (status == SubscriptionStatus.CANCELLED) {
            throw new IllegalStateException("已取消的訂閱不得變更方案,需建立新訂閱(不變量 B3)");
        }
        PlanCode previous = planCode;
        this.planId = plan.id();
        this.planCode = plan.code();
        this.status = SubscriptionStatus.ACTIVE;
        this.period = Objects.requireNonNull(newPeriod, "newPeriod 不得為 null");
        pendingEvents.record(new SubscriptionChanged(tenantId, id, previous, planCode, status));
    }

    public void cancel(Instant now) {
        if (status == SubscriptionStatus.CANCELLED) {
            return;
        }
        this.status = SubscriptionStatus.CANCELLED;
        this.cancelledAt = Objects.requireNonNull(now, "now 不得為 null");
        pendingEvents.record(new SubscriptionChanged(tenantId, id, planCode, planCode, status));
    }

    /**
     * 這份訂閱此刻實際生效的方案(B4)。
     *
     * <p>非 ACTIVE(取消、逾期、過期)或計費區間已結束者一律降回 {@code FREE}——
     * 與「沒有訂閱的已登入 tenant 視為 FREE」同一條規則。規格未定義 PAST_DUE 的配額語意,
     * 依 00 §0.4 的優先序取安全側(最小權限)。
     */
    public PlanCode effectivePlanCode(Instant now) {
        if (status != SubscriptionStatus.ACTIVE || period.hasEndedBy(now)) {
            return PlanCode.FREE;
        }
        return planCode;
    }

    public List<DomainEvent> pullEvents() {
        return pendingEvents.pull();
    }

    public SubscriptionSnapshot snapshot() {
        return new SubscriptionSnapshot(
                id, tenantId, planId, status, provider, externalSubscriptionId, period, cancelledAt);
    }

    public SubscriptionId id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public PlanId planId() {
        return planId;
    }

    public PlanCode planCode() {
        return planCode;
    }

    public SubscriptionStatus status() {
        return status;
    }

    public SubscriptionProvider provider() {
        return provider;
    }

    public BillingPeriod period() {
        return period;
    }

    public Instant cancelledAt() {
        return cancelledAt;
    }
}
