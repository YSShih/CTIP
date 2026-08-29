package com.ctip.domain.event;

import com.ctip.domain.notification.WebhookId;
import com.ctip.domain.tenant.TenantId;

/**
 * Webhook 聚合發佈的 M3 事件(docs/spec/02-ddd-model.md §2.4)。
 * 消費者為 Notification(M3)與 Audit(M3)。
 *
 * <p>§2.3 的 W3 原寫「發出 {@code SystemAlert}」,但 §2.4 的事件清單裡沒有那個事件;
 * 以本事件為準(2026-08-28,[ADR 0021](../architecture/decisions/0021-phase20-23-spec-resolutions.md) 第 2 節)。
 */
public interface WebhookEvents {

    /** 不變量 W3:連續失敗達門檻,webhook 已被系統停用。 */
    record WebhookDisabled(TenantId tenantId, WebhookId webhookId, int consecutiveFailures) implements DomainEvent {}
}
