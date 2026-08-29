package com.ctip.domain.notification;

import com.ctip.domain.event.DomainEvent;
import com.ctip.domain.event.PendingEvents;
import com.ctip.domain.event.WebhookEvents;
import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.user.UserId;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Webhook 聚合根,不變量 W1–W6(docs/spec/02-ddd-model.md §2.3、03 §3.2.9)。
 *
 * <ul>
 *   <li>W1 {@code targetUrl} 必須 {@code https://} —— 本類別 + {@code ck_wh_https}</li>
 *   <li>W2 密鑰不以明文落庫 —— {@link HmacSecret} 由基礎設施以 AES-GCM 加解密(ADR 0021)</li>
 *   <li>W3 {@code consecutiveFailures} 達 5 → {@code DISABLED} + {@code WebhookDisabled}</li>
 *   <li>W4 送達重試最多 5 次 —— {@link #MAX_ATTEMPTS} 與退避表</li>
 *   <li>W5 過濾在伺服器端 —— {@link #matches(NotificationEvent)}</li>
 *   <li>W6 每租戶數量上限 —— 建立時由 application 層對 {@code plans.max_webhooks} 檢查</li>
 * </ul>
 */
public final class Webhook {

    /** 不變量 W4:單一事件最多嘗試五次(首次 + 四次重試)。 */
    public static final int MAX_ATTEMPTS = 5;

    /** 不變量 W3:連續五個事件皆用盡重試 → 停用。 */
    public static final int FAILURE_THRESHOLD = 5;

    private static final String REQUIRED_SCHEME = "https://";

    private final PendingEvents pendingEvents = new PendingEvents();

    private final WebhookId id;
    private final TenantId tenantId;
    private final UserId createdByUserId;
    private final String targetUrl;
    private final HmacSecret secret;
    private final Set<NotificationType> eventTypes;
    private final WebhookFilter filter;
    private final Instant createdAt;
    private String name;
    private WebhookStatus status;
    private int consecutiveFailures;
    private Instant lastDeliveryAt;
    private Instant lastSuccessAt;

    private Webhook(WebhookSnapshot s) {
        this.id = Objects.requireNonNull(s.id(), "id 不得為 null");
        this.tenantId = Objects.requireNonNull(s.tenantId(), "tenantId 不得為 null");
        this.createdByUserId = Objects.requireNonNull(s.createdByUserId(), "createdByUserId 不得為 null");
        this.name = requireName(s.name());
        this.targetUrl = requireHttps(s.targetUrl());
        this.secret = Objects.requireNonNull(s.secret(), "secret 不得為 null");
        this.eventTypes = requireEventTypes(s.eventTypes());
        this.filter = s.filter() == null ? WebhookFilter.unfiltered() : s.filter();
        this.status = Objects.requireNonNull(s.status(), "status 不得為 null");
        this.consecutiveFailures = requireNonNegative(s.consecutiveFailures());
        this.lastDeliveryAt = s.lastDeliveryAt();
        this.lastSuccessAt = s.lastSuccessAt();
        this.createdAt = Objects.requireNonNull(s.createdAt(), "createdAt 不得為 null");
        if (tenantId.isPublic()) {
            throw new IllegalArgumentException("public tenant 不得有 webhook(不變量 T3)");
        }
    }

    /** 建立一個 {@code ACTIVE} 的 webhook;W6 的數量上限由呼叫端先行檢查。 */
    public static Webhook register(WebhookSnapshot snapshot) {
        if (snapshot.status() != WebhookStatus.ACTIVE) {
            throw new IllegalArgumentException("新建立的 webhook 必須為 ACTIVE");
        }
        if (snapshot.consecutiveFailures() != 0) {
            throw new IllegalArgumentException("新建立的 webhook 不得帶失敗計數");
        }
        return new Webhook(snapshot);
    }

    /** 由持久化狀態重建(不重放事件,僅重新驗證不變量)。 */
    public static Webhook reconstitute(WebhookSnapshot snapshot) {
        return new Webhook(snapshot);
    }

    /**
     * 不變量 W5:是否應對此事件送達。三個條件——狀態、訂閱的事件型別、過濾條件——
     * 全部在伺服器端判定。
     */
    public boolean matches(NotificationEvent event) {
        return status == WebhookStatus.ACTIVE
                && event.isVisibleTo(tenantId)
                && eventTypes.contains(event.type())
                && filter.accepts(event);
    }

    /** {@code X-CTIP-Signature} 的 hex;payload 由 {@link WebhookSignature#payload} 組成。 */
    public String sign(byte[] payload) {
        return secret.hex(payload);
    }

    /**
     * 記錄一次<strong>事件層級</strong>的送達結果。
     *
     * <p>{@code consecutiveFailures} 計的是「連續幾個事件用盡了重試」,不是嘗試次數:
     * 若它計嘗試次數,單一個 {@code ABANDONED} 事件(五次嘗試)就會立刻觸發 W3,
     * W3 便完全等同於 W4,規格不會分成兩條不變量。{@code FAILED}(仍會重試)因此
     * 只更新 {@code lastDeliveryAt}。
     */
    public void recordDelivery(DeliveryStatus outcome, Instant now) {
        Objects.requireNonNull(outcome, "outcome 不得為 null");
        Objects.requireNonNull(now, "now 不得為 null");
        this.lastDeliveryAt = now;
        switch (outcome) {
            case SUCCESS -> {
                this.consecutiveFailures = 0;
                this.lastSuccessAt = now;
            }
            case ABANDONED -> {
                this.consecutiveFailures++;
                if (consecutiveFailures >= FAILURE_THRESHOLD && status == WebhookStatus.ACTIVE) {
                    this.status = WebhookStatus.DISABLED;
                    pendingEvents.record(new WebhookEvents.WebhookDisabled(tenantId, id, consecutiveFailures));
                }
            }
            case PENDING, FAILED -> {
                // 仍在 W4 的重試次數內,尚未構成一次「連續失敗」
            }
        }
    }

    /** 租戶自行暫停;不歸零失敗計數(恢復後仍在同一段連續失敗中)。 */
    public void suspend() {
        if (status == WebhookStatus.ACTIVE) {
            this.status = WebhookStatus.SUSPENDED;
        }
    }

    /** 由暫停恢復。{@code DISABLED} 是終態(W3),不得由此復活。 */
    public void resume() {
        if (status == WebhookStatus.SUSPENDED) {
            this.status = WebhookStatus.ACTIVE;
            this.consecutiveFailures = 0;
        }
    }

    public void rename(String newName) {
        this.name = requireName(newName);
    }

    public List<DomainEvent> pullEvents() {
        return pendingEvents.pull();
    }

    public WebhookSnapshot snapshot() {
        return new WebhookSnapshot(
                id,
                tenantId,
                createdByUserId,
                name,
                targetUrl,
                secret,
                Set.copyOf(eventTypes),
                filter,
                status,
                consecutiveFailures,
                lastDeliveryAt,
                lastSuccessAt,
                createdAt);
    }

    private static String requireHttps(String url) {
        if (url == null || !url.startsWith(REQUIRED_SCHEME)) {
            throw new IllegalArgumentException("targetUrl 必須為 https://(不變量 W1):" + url);
        }
        return url;
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name 不得為空");
        }
        return name;
    }

    /** 訂閱零個事件型別的 webhook 永遠不會送達任何東西——那是不可達的設定,不是有效狀態。 */
    private static Set<NotificationType> requireEventTypes(Set<NotificationType> types) {
        if (types == null || types.isEmpty()) {
            throw new IllegalArgumentException("eventTypes 至少要有一個型別");
        }
        return EnumSet.copyOf(types);
    }

    private static int requireNonNegative(int failures) {
        if (failures < 0) {
            throw new IllegalArgumentException("consecutiveFailures 不得為負:" + failures);
        }
        return failures;
    }

    public WebhookId id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public UserId createdByUserId() {
        return createdByUserId;
    }

    public String name() {
        return name;
    }

    public String targetUrl() {
        return targetUrl;
    }

    public Set<NotificationType> eventTypes() {
        return Set.copyOf(eventTypes);
    }

    public WebhookFilter filter() {
        return filter;
    }

    public WebhookStatus status() {
        return status;
    }

    public int consecutiveFailures() {
        return consecutiveFailures;
    }

    public Instant lastDeliveryAt() {
        return lastDeliveryAt;
    }

    public Instant lastSuccessAt() {
        return lastSuccessAt;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
