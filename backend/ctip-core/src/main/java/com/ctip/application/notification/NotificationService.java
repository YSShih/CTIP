package com.ctip.application.notification;

import com.ctip.application.port.ClockPort;
import com.ctip.application.port.IdGeneratorPort;
import com.ctip.application.port.NotificationPort;
import com.ctip.application.port.RealtimePushPort;
import com.ctip.domain.notification.NotificationEvent;
import com.ctip.domain.shared.CursorPage;
import com.ctip.domain.tenant.TenantId;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 站內通知(docs/spec/13-platform-ops.md §13.2、04 表 26)。
 *
 * <p>{@link #dispatch} 是通知管線的<strong>唯一入口</strong>——Kafka 消費端與
 * 程序內轉發(mvp/dev 沒有 broker)都經過它,兩條路徑的副作用因此完全相同。
 * 它必須是冪等的(§13.1 規則 5):同一個 {@code eventId} 重送不得產生第二列通知、
 * 不得重複推播、不得重複送達 webhook。
 */
@Service
public class NotificationService {

    private final NotificationPort notifications;
    private final NotificationTransactions transactions;
    private final RealtimePushPort realtime;
    private final WebhookDeliveryService deliveries;
    private final Ports ports;

    public NotificationService(
            NotificationPort notifications,
            NotificationTransactions transactions,
            RealtimePushPort realtime,
            WebhookDeliveryService deliveries,
            Ports ports) {
        this.notifications = notifications;
        this.transactions = transactions;
        this.realtime = realtime;
        this.deliveries = deliveries;
        this.ports = ports;
    }

    /** 時間與識別碼一律經 port 取得(ArchUnit 規則 9);兩者一起注入以免建構子參數超過上限。 */
    @Component
    public record Ports(IdGeneratorPort idGenerator, ClockPort clock) {}

    /**
     * 落庫 → 推播 → webhook 扇出。
     *
     * <p>三個副作用<strong>各自獨立冪等</strong>,不靠先後順序:通知列靠
     * {@code ux_notif_idempotent}、送達列靠 {@code ux_wd_idempotent}、推播只在
     * 通知列真的是這次插入時才發(否則重送會讓 UI 跳兩次)。若改成「通知列已存在就整個跳過」,
     * 第一次處理到一半崩潰的事件將永遠不會補送 webhook。
     */
    public void dispatch(NotificationEvent event) {
        NotificationTransactions.Persisted persisted = transactions.persist(new NotificationRecord(
                ports.idGenerator().nextId(),
                event.tenantId(),
                event.userId(),
                event.eventId(),
                event.type(),
                event.title(),
                event.body(),
                event.severity(),
                event.resourceType(),
                event.resourceId(),
                null,
                event.occurredAt()));
        if (persisted.inserted()) {
            realtime.push(persisted.notification());
        }
        deliveries.fanOut(event, persisted.notification());
    }

    @Transactional(readOnly = true)
    public CursorPage<NotificationRecord> list(NotificationQuery query) {
        return notifications.list(query);
    }

    /** @return false 表示該通知不存在或不在呼叫者的可見範圍內(controller 回 404) */
    @Transactional
    public boolean markRead(UUID id, TenantId tenantId, UUID userId) {
        return notifications.markRead(id, tenantId, userId, ports.clock().now());
    }

    @Transactional(readOnly = true)
    public long countUnread(TenantId tenantId, UUID userId) {
        return notifications.countUnread(tenantId, userId);
    }
}
