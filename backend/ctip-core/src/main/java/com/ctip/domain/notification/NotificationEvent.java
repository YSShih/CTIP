package com.ctip.domain.notification;

import com.ctip.domain.tenant.TenantId;
import com.ctip.sdk.IocType;
import com.ctip.sdk.Severity;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 通知形狀的事件投影——{@code ctip.notification.events.v1} 的 payload,也是
 * {@link Webhook#matches(NotificationEvent)} 與 {@link WebhookFilter#accepts(NotificationEvent)} 的輸入。
 *
 * <p><strong>為什麼不是直接拿 {@code DomainEvent}</strong>(03 §3.2.9 的圖寫的是
 * {@code matches(DomainEvent)}):{@code WebhookFilter} 要依 {@code iocTypes} / {@code minSeverity} /
 * {@code tags} / {@code sourceIds} 過濾,而 §2.4 的 domain event 身上<strong>沒有這些欄位</strong>
 * ——{@code IndicatorCreated} 只帶 {@code type} 與 {@code tlp},severity 與 tags 是合併後才定的。
 * 把欄位補進事件等於修改發佈端,而 §13.1 明文「發佈端程式碼永不修改」。
 * 因此由 application 層在事件送上 Kafka 之前補齊成本型別,過濾仍完全在伺服器端(不變量 W5)。
 * 定調見 ADR 0029。
 *
 * @param eventId      domain event 信封的 {@code eventId};冪等鍵(§13.1 規則 5)
 * @param type         七種通知型別之一
 * @param tenantId     事件歸屬租戶;public tenant 代表平台範圍,對所有已認證租戶可見
 * @param occurredAt   信封的 {@code occurredAt}
 * @param traceId      信封的 {@code traceId};可為 null(非請求路徑觸發的事件沒有 trace)
 * @param title        站內通知標題
 * @param body         站內通知內容;可為 null
 * @param severity     事件嚴重度,對應 {@code WebhookFilter.minSeverity}
 * @param resourceType 例 {@code indicator}、{@code source};可為 null
 * @param resourceId   對應資源的 id;可為 null
 * @param userId       僅該使用者可見的通知(例:token 重用);null = 全租戶廣播
 * @param iocTypes     過濾用;空 = 該事件與 IOC 型別無關,{@code filterIocTypes} 非空時不通過
 * @param tags         過濾用;空 = 無標籤
 * @param sourceIds    過濾用;空 = 與來源無關
 */
public record NotificationEvent(
        UUID eventId,
        NotificationType type,
        TenantId tenantId,
        Instant occurredAt,
        String traceId,
        String title,
        String body,
        Severity severity,
        String resourceType,
        UUID resourceId,
        UUID userId,
        Set<IocType> iocTypes,
        Set<String> tags,
        Set<UUID> sourceIds) {

    public NotificationEvent {
        Objects.requireNonNull(eventId, "eventId 不得為 null");
        Objects.requireNonNull(type, "type 不得為 null");
        Objects.requireNonNull(tenantId, "tenantId 不得為 null");
        Objects.requireNonNull(occurredAt, "occurredAt 不得為 null");
        Objects.requireNonNull(severity, "severity 不得為 null");
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title 不得為空");
        }
        iocTypes = iocTypes == null ? Set.of() : Set.copyOf(iocTypes);
        tags = tags == null ? Set.of() : Set.copyOf(tags);
        sourceIds = sourceIds == null ? Set.of() : Set.copyOf(sourceIds);
    }

    /**
     * 本事件是否對 {@code viewer} 租戶可見。
     *
     * <p>平台範圍的事件掛在 public tenant 上,對所有租戶可見——與 §7.9 的
     * 「過濾為 {@code IN (current, public)}」同一條規則(v2.0 修正的規格衝突 7)。
     * 租戶自有的事件只回自己那一份。
     */
    public boolean isVisibleTo(TenantId viewer) {
        return tenantId.isPublic() || tenantId.equals(viewer);
    }
}
