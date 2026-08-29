package com.ctip.application.notification;

import com.ctip.application.port.IndicatorRepository;
import com.ctip.application.port.ThreatRepository;
import com.ctip.domain.event.BloomEvents;
import com.ctip.domain.event.DomainEvent;
import com.ctip.domain.event.IndicatorEvents;
import com.ctip.domain.event.IngestionEvents;
import com.ctip.domain.event.SourceEvents;
import com.ctip.domain.event.SubscriptionEvents;
import com.ctip.domain.event.ThreatEvents;
import com.ctip.domain.event.UserEvents;
import com.ctip.domain.event.WebhookEvents;
import com.ctip.domain.indicator.Indicator;
import com.ctip.domain.indicator.IndicatorId;
import com.ctip.domain.notification.EventContext;
import com.ctip.domain.notification.NotificationEvent;
import com.ctip.domain.notification.NotificationType;
import com.ctip.domain.threat.Threat;
import com.ctip.domain.threat.ThreatSnapshot;
import com.ctip.sdk.Severity;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * domain event → {@link NotificationEvent} 的唯一對應點
 * (docs/spec/02-ddd-model.md §2.4 的「消費者」欄 + 13 §13.2 的七種事件型別;
 * 對照表另寫入 {@code docs/api/events/README.md})。
 *
 * <p><strong>為什麼要這一層</strong>:{@code WebhookFilter} 要依 severity / tags / sourceIds 過濾,
 * 而 §2.4 的事件身上沒有這些欄位,補進去等於修改發佈端(§13.1 明文禁止)。
 * 因此在事件送出之前由聚合補齊——補齊發生在 AFTER_COMMIT,讀到的就是已提交的狀態。
 *
 * <p>回傳 {@link Optional#empty()} 表示該事件不產生通知(例:{@code IngestionStarted}、
 * {@code ApiKeyCreated} ——§2.4 給它們的消費者是 Audit/Metrics,不是 Notification)。
 * 它仍會被轉發到自己的 Kafka topic。
 */
@Service
public class NotificationEventFactory {

    private final IndicatorRepository indicators;
    private final ThreatRepository threats;

    public NotificationEventFactory(IndicatorRepository indicators, ThreatRepository threats) {
        this.indicators = indicators;
        this.threats = threats;
    }

    /**
     * {@code REQUIRES_NEW}:呼叫端在 AFTER_COMMIT 的回呼內,那裡的交易已經結束
     * (02 §2.4 對 AFTER_COMMIT 消費端的規則)。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<NotificationEvent> from(EventContext context, DomainEvent event) {
        return switch (event) {
            case IndicatorEvents.IndicatorCreated e -> Optional.of(indicatorCreated(context, e));
            case IndicatorEvents.IndicatorMerged e -> Optional.of(indicatorMerged(context, e));
            case IndicatorEvents.IndicatorRevoked e -> Optional.of(indicatorRevoked(context, e));
            case ThreatEvents.ThreatUpdated e -> Optional.of(threatUpdated(context, e));
            case SourceEvents.SourceDegraded e -> Optional.of(sourceDegraded(context, e));
            case SourceEvents.SourceFailed e -> Optional.of(sourceFailed(context, e));
            case SourceEvents.SourceRecovered e -> Optional.of(sourceRecovered(context, e));
            case IngestionEvents.IngestionFailed e -> Optional.of(ingestionFailed(context, e));
            case SubscriptionEvents.SubscriptionChanged e -> Optional.of(subscriptionChanged(context, e));
            case BloomEvents.BloomSnapshotReady e -> Optional.of(snapshotReady(context, e));
            case UserEvents.TokenReuseDetected e -> Optional.of(tokenReuseDetected(context, e));
            case WebhookEvents.WebhookDisabled e -> Optional.of(webhookDisabled(context, e));
            default -> Optional.empty();
        };
    }

    private NotificationEvent indicatorCreated(EventContext context, IndicatorEvents.IndicatorCreated event) {
        return fromIndicator(
                context,
                event,
                new NotificationContent(
                        NotificationType.NEW_IOC,
                        Severity.INFO,
                        "新增 IOC:" + event.normalizedValue(),
                        "型別 " + event.type() + ",TLP " + event.tlp(),
                        "indicator",
                        event.indicatorId().value(),
                        null),
                event.indicatorId());
    }

    private NotificationEvent indicatorMerged(EventContext context, IndicatorEvents.IndicatorMerged event) {
        return fromIndicator(
                context,
                event,
                new NotificationContent(
                        NotificationType.NEW_IOC,
                        Severity.INFO,
                        "IOC 取得新的來源佐證",
                        "來源 " + event.mergedSourceId().value() + " 回報了既有的 IOC",
                        "indicator",
                        event.indicatorId().value(),
                        null),
                event.indicatorId());
    }

    private NotificationEvent indicatorRevoked(EventContext context, IndicatorEvents.IndicatorRevoked event) {
        return fromIndicator(
                context,
                event,
                new NotificationContent(
                        NotificationType.IOC_REVOKED,
                        Severity.INFO,
                        "IOC 已撤銷",
                        "由來源 " + event.revokedBy().value() + " 撤銷",
                        "indicator",
                        event.indicatorId().value(),
                        null),
                event.indicatorId());
    }

    private NotificationEvent sourceDegraded(EventContext context, SourceEvents.SourceDegraded event) {
        return sourceHealth(
                context,
                event,
                event.sourceId().value(),
                Severity.MEDIUM,
                "來源健康度下降:連續失敗 " + event.consecutiveFailures() + " 次");
    }

    private NotificationEvent sourceFailed(EventContext context, SourceEvents.SourceFailed event) {
        return sourceHealth(
                context,
                event,
                event.sourceId().value(),
                Severity.HIGH,
                "來源已停用:連續失敗 " + event.consecutiveFailures() + " 次,已停止同步");
    }

    /**
     * §13.2 的七種型別裡沒有「來源恢復」,它與失敗共用來源健康這個頻道
     * (severity {@code INFO});訂閱來源失敗的人本來就需要知道它恢復了(ADR 0029)。
     */
    private NotificationEvent sourceRecovered(EventContext context, SourceEvents.SourceRecovered event) {
        return sourceHealth(context, event, event.sourceId().value(), Severity.INFO, "來源已恢復:同步恢復正常");
    }

    private NotificationEvent ingestionFailed(EventContext context, IngestionEvents.IngestionFailed event) {
        return sourceHealth(context, event, event.sourceId().value(), Severity.HIGH, "情資攝取失敗:" + event.maskedReason());
    }

    private NotificationEvent subscriptionChanged(EventContext context, SubscriptionEvents.SubscriptionChanged event) {
        return plain(
                context,
                event,
                new NotificationContent(
                        NotificationType.SUBSCRIPTION_CHANGED,
                        Severity.INFO,
                        "方案已變更",
                        event.previousPlan() + " → " + event.newPlan() + "(" + event.status() + ")",
                        "subscription",
                        event.subscriptionId().value(),
                        null));
    }

    private NotificationEvent snapshotReady(EventContext context, BloomEvents.BloomSnapshotReady event) {
        return plain(
                context,
                event,
                new NotificationContent(
                        NotificationType.SYNC_SNAPSHOT_READY,
                        Severity.INFO,
                        "Bloom snapshot 已就緒",
                        event.scope() + " 版本 " + event.bloomVersion() + ",成員數 " + event.memberCount(),
                        "bloom",
                        null,
                        null));
    }

    /** 帳號安全事件只給當事人看,不廣播給全租戶。 */
    private NotificationEvent tokenReuseDetected(EventContext context, UserEvents.TokenReuseDetected event) {
        return plain(
                context,
                event,
                new NotificationContent(
                        NotificationType.SYSTEM_ALERT,
                        Severity.CRITICAL,
                        "偵測到 refresh token 重用",
                        "該 token family 已全數撤銷,請重新登入並檢查裝置",
                        "user",
                        event.userId().value(),
                        event.userId().value()));
    }

    private NotificationEvent webhookDisabled(EventContext context, WebhookEvents.WebhookDisabled event) {
        return plain(
                context,
                event,
                new NotificationContent(
                        NotificationType.SYSTEM_ALERT,
                        Severity.HIGH,
                        "Webhook 已停用",
                        "連續 " + event.consecutiveFailures() + " 次送達失敗,已停用(不變量 W3)",
                        "webhook",
                        event.webhookId().value(),
                        null));
    }

    private NotificationEvent threatUpdated(EventContext context, ThreatEvents.ThreatUpdated event) {
        Optional<ThreatSnapshot> snapshot = threats.findById(event.threatId()).map(Threat::snapshot);
        return new NotificationContent(
                        NotificationType.THREAT_UPDATED,
                        snapshot.map(ThreatSnapshot::severity).orElse(Severity.INFO),
                        "威脅實體已更新:" + snapshot.map(ThreatSnapshot::name).orElse("(已移除)"),
                        "變更類型 " + event.change(),
                        "threat",
                        event.threatId().value(),
                        null)
                .toEvent(
                        context,
                        event,
                        Set.of(),
                        snapshot.map(ThreatSnapshot::tags).orElse(Set.of()),
                        Set.of());
    }

    /**
     * Indicator 事件的補齊:severity 與 tags 是<strong>合併之後</strong>才定的,
     * sourceIds 更是只存在於聚合裡——三者都是 {@code WebhookFilter} 的過濾維度。
     * 讀不到聚合(例如同批次內已被合併掉)時退回 {@code content} 帶得動的資訊,通知照發。
     */
    private NotificationEvent fromIndicator(
            EventContext context, DomainEvent event, NotificationContent content, IndicatorId indicatorId) {
        Optional<Indicator> indicator = indicators.findById(indicatorId);
        return content.withSeverity(indicator.map(Indicator::severity).orElse(content.severity()))
                .toEvent(
                        context,
                        event,
                        indicator.map(found -> Set.of(found.value().type())).orElse(Set.of()),
                        indicator.map(Indicator::tags).orElse(Set.of()),
                        indicator.map(NotificationEventFactory::sourceIdsOf).orElse(Set.of()));
    }

    private static Set<UUID> sourceIdsOf(Indicator indicator) {
        return indicator.snapshot().sources().stream()
                .map(source -> source.sourceId().value())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private NotificationEvent sourceHealth(
            EventContext context, DomainEvent event, UUID sourceId, Severity severity, String title) {
        return NotificationContent.sourceHealth(context, event, sourceId, severity, title);
    }

    private NotificationEvent plain(EventContext context, DomainEvent event, NotificationContent content) {
        return content.toEvent(context, event);
    }
}
