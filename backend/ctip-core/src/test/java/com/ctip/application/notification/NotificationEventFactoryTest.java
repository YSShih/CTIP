package com.ctip.application.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctip.domain.event.ApiKeyEvents;
import com.ctip.domain.event.BloomEvents;
import com.ctip.domain.event.DomainEvent;
import com.ctip.domain.event.IndicatorEvents;
import com.ctip.domain.event.IngestionEvents;
import com.ctip.domain.event.SourceEvents;
import com.ctip.domain.event.SubscriptionEvents;
import com.ctip.domain.event.ThreatEvents;
import com.ctip.domain.event.UserEvents;
import com.ctip.domain.event.WebhookEvents;
import com.ctip.domain.identity.ApiKeyId;
import com.ctip.domain.indicator.Indicator;
import com.ctip.domain.notification.EventContext;
import com.ctip.domain.notification.NotificationEvent;
import com.ctip.domain.notification.NotificationType;
import com.ctip.domain.notification.WebhookId;
import com.ctip.domain.plan.PlanCode;
import com.ctip.domain.plan.SubscriptionId;
import com.ctip.domain.plan.SubscriptionStatus;
import com.ctip.domain.source.SourceId;
import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.threat.ThreatChange;
import com.ctip.domain.threat.ThreatId;
import com.ctip.domain.user.TokenFamilyId;
import com.ctip.domain.user.UserId;
import com.ctip.sdk.IocType;
import com.ctip.sdk.Severity;
import com.ctip.sdk.Tlp;
import com.ctip.testing.InMemoryIndicatorRepository;
import com.ctip.testing.InMemoryThreatRepository;
import com.ctip.testing.IndicatorTestBuilder;
import com.ctip.testing.ThreatTestBuilder;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * domain event → 七種通知型別的對應(docs/api/events/README.md 的對照表;13 §13.2)。
 *
 * <p>另外驗一件 ADR 0029 的核心主張:{@code WebhookFilter} 要的 severity / tags / sourceIds
 * <strong>不在事件上</strong>,必須從聚合補齊。
 */
@Tag("unit")
class NotificationEventFactoryTest {

    private static final EventContext CONTEXT = new EventContext(
            UUID.fromString("6f1d2f52-6f0a-4a6f-9a0f-2f1b6d0a1c33"), Instant.parse("2026-08-29T09:15:04Z"), "trace-1");
    private static final TenantId TENANT = IndicatorTestBuilder.DEMO_TENANT;
    private static final SourceId SOURCE = new SourceId(new UUID(0, 5));

    private final InMemoryIndicatorRepository indicators = new InMemoryIndicatorRepository();
    private final InMemoryThreatRepository threats = new InMemoryThreatRepository();
    private final NotificationEventFactory factory = new NotificationEventFactory(indicators, threats);

    @Test
    void indicatorEventsMapToTheirNotificationTypes() {
        Indicator indicator = activeIndicator();
        indicators.save(indicator);

        assertThat(typeOf(new IndicatorEvents.IndicatorCreated(
                        indicator.id(), TENANT, IocType.DOMAIN, "mapped", Tlp.CLEAR)))
                .isEqualTo(NotificationType.NEW_IOC);
        assertThat(typeOf(new IndicatorEvents.IndicatorMerged(indicator.id(), TENANT, SOURCE)))
                .isEqualTo(NotificationType.NEW_IOC);
        assertThat(typeOf(new IndicatorEvents.IndicatorRevoked(indicator.id(), TENANT, SOURCE)))
                .isEqualTo(NotificationType.IOC_REVOKED);
    }

    /** ADR 0029 的核心:過濾維度來自聚合,不是事件。 */
    @Test
    void theFilterDimensionsAreTakenFromTheAggregateNotTheEvent() {
        Indicator indicator = activeIndicator();
        indicators.save(indicator);

        NotificationEvent notification = require(
                new IndicatorEvents.IndicatorCreated(indicator.id(), TENANT, IocType.DOMAIN, "enriched", Tlp.CLEAR));

        assertThat(notification.severity()).isEqualTo(indicator.severity());
        assertThat(notification.tags()).isEqualTo(indicator.tags());
        assertThat(notification.iocTypes()).containsExactly(indicator.value().type());
        assertThat(notification.sourceIds()).isNotEmpty();
    }

    /** 聚合讀不到時通知照發,只是沒有過濾維度——漏一則通知比整條管線中斷糟糕得多。 */
    @Test
    void aMissingAggregateDegradesGracefully() {
        NotificationEvent notification = require(new IndicatorEvents.IndicatorCreated(
                new com.ctip.domain.indicator.IndicatorId(new UUID(0, 99)),
                TENANT,
                IocType.IPV4,
                "203.0.113.9",
                Tlp.CLEAR));

        assertThat(notification.severity()).isEqualTo(Severity.INFO);
        assertThat(notification.iocTypes()).isEmpty();
        assertThat(notification.sourceIds()).isEmpty();
    }

    /** 四種來源／攝取事件共用 SOURCE_FAILURE——七種型別裡只有它承載來源狀態(ADR 0029)。 */
    @Test
    void sourceHealthAndIngestionFailuresShareTheSourceChannel() {
        assertThat(typeOf(new SourceEvents.SourceDegraded(SOURCE, 3))).isEqualTo(NotificationType.SOURCE_FAILURE);
        assertThat(typeOf(new SourceEvents.SourceFailed(SOURCE, 5))).isEqualTo(NotificationType.SOURCE_FAILURE);
        assertThat(typeOf(new SourceEvents.SourceRecovered(SOURCE))).isEqualTo(NotificationType.SOURCE_FAILURE);
        assertThat(typeOf(new IngestionEvents.IngestionFailed(SOURCE, "timeout")))
                .isEqualTo(NotificationType.SOURCE_FAILURE);

        // 恢復是 INFO,不是 HIGH——同一個頻道但不同的嚴重度
        assertThat(require(new SourceEvents.SourceRecovered(SOURCE)).severity()).isEqualTo(Severity.INFO);
        assertThat(require(new SourceEvents.SourceFailed(SOURCE, 5)).severity()).isEqualTo(Severity.HIGH);
        // 每則都帶 sourceId,filterSourceIds 才有東西可以比對
        assertThat(require(new SourceEvents.SourceFailed(SOURCE, 5)).sourceIds())
                .containsExactly(SOURCE.value());
    }

    @Test
    void platformEventsMapToTheRemainingTypes() {
        assertThat(typeOf(new SubscriptionEvents.SubscriptionChanged(
                        TENANT,
                        new SubscriptionId(new UUID(0, 6)),
                        PlanCode.FREE,
                        PlanCode.PREMIUM,
                        SubscriptionStatus.ACTIVE)))
                .isEqualTo(NotificationType.SUBSCRIPTION_CHANGED);
        assertThat(typeOf(new BloomEvents.BloomSnapshotReady(
                        TENANT, com.ctip.domain.bloom.BloomScope.PUBLIC, 1L, 2L, 3L)))
                .isEqualTo(NotificationType.SYNC_SNAPSHOT_READY);
        assertThat(typeOf(new WebhookEvents.WebhookDisabled(TENANT, new WebhookId(new UUID(0, 7)), 5)))
                .isEqualTo(NotificationType.SYSTEM_ALERT);
    }

    /** token 重用是帳號安全事件,只給當事人,不廣播給全租戶。 */
    @Test
    void tokenReuseIsAddressedToTheUserAlone() {
        UserId user = new UserId(new UUID(0, 8));
        NotificationEvent notification =
                require(new UserEvents.TokenReuseDetected(TENANT, user, new TokenFamilyId(new UUID(0, 9))));

        assertThat(notification.type()).isEqualTo(NotificationType.SYSTEM_ALERT);
        assertThat(notification.severity()).isEqualTo(Severity.CRITICAL);
        assertThat(notification.userId()).isEqualTo(user.value());
    }

    @Test
    void threatUpdatesCarryTheAggregatesSeverityAndTags() {
        com.ctip.domain.threat.Threat threat = ThreatTestBuilder.threat(
                ThreatTestBuilder.THREAT_ID,
                TENANT,
                com.ctip.domain.threat.ThreatType.CAMPAIGN,
                "Op Nightfall",
                Tlp.AMBER);
        threats.save(threat);

        NotificationEvent notification =
                require(new ThreatEvents.ThreatUpdated(threat.id(), TENANT, ThreatChange.CREATED));
        assertThat(notification.type()).isEqualTo(NotificationType.THREAT_UPDATED);
        assertThat(notification.title()).contains("Op Nightfall");
        assertThat(notification.resourceId()).isEqualTo(threat.id().value());
    }

    /** 已被刪掉的 Threat 仍要發得出通知,標題說清楚它不在了。 */
    @Test
    void aThreatUpdateForAMissingAggregateStillProducesANotification() {
        NotificationEvent notification = require(
                new ThreatEvents.ThreatUpdated(new ThreatId(new UUID(0, 77)), TENANT, ThreatChange.STATUS_CHANGED));
        assertThat(notification.title()).contains("已移除");
    }

    /** §2.4 沒有把 Notification 列為消費者的事件不產生通知,但仍會進自己的 Kafka topic。 */
    @Test
    void eventsWithoutANotificationConsumerProduceNothing() {
        assertThat(factory.from(CONTEXT, new IngestionEvents.IngestionStarted(SOURCE)))
                .isEmpty();
        assertThat(factory.from(CONTEXT, new ApiKeyEvents.ApiKeyCreated(TENANT, new ApiKeyId(new UUID(0, 10)))))
                .isEmpty();
        assertThat(factory.from(
                        CONTEXT,
                        new IndicatorEvents.IndicatorExpired(
                                new com.ctip.domain.indicator.IndicatorId(new UUID(0, 11)),
                                TENANT,
                                Instant.parse("2026-08-29T00:00:00Z"))))
                .isEmpty();
    }

    /** 信封的三個欄位原樣沿用——特別是 eventId,它是冪等鍵。 */
    @Test
    void theEnvelopeFieldsArePreserved() {
        NotificationEvent notification = require(new SourceEvents.SourceRecovered(SOURCE));
        assertThat(notification.eventId()).isEqualTo(CONTEXT.eventId());
        assertThat(notification.occurredAt()).isEqualTo(CONTEXT.occurredAt());
        assertThat(notification.traceId()).isEqualTo(CONTEXT.traceId());
    }

    private static Indicator activeIndicator() {
        return IndicatorTestBuilder.activeIndicator(
                TENANT, Tlp.CLEAR, com.ctip.sdk.RedistributionPolicy.PUBLIC_REDISTRIBUTABLE);
    }

    private NotificationType typeOf(DomainEvent event) {
        return require(event).type();
    }

    private NotificationEvent require(DomainEvent event) {
        Optional<NotificationEvent> notification = factory.from(CONTEXT, event);
        assertThat(notification).as("%s 應產生通知", event.eventType()).isPresent();
        return notification.orElseThrow();
    }
}
