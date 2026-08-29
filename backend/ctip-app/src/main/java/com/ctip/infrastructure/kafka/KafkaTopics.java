package com.ctip.infrastructure.kafka;

import com.ctip.domain.event.ApiKeyEvents;
import com.ctip.domain.event.BloomEvents;
import com.ctip.domain.event.DomainEvent;
import com.ctip.domain.event.IndicatorEvents;
import com.ctip.domain.event.IngestionEvents;
import com.ctip.domain.event.SourceEvents;
import com.ctip.domain.event.SubscriptionEvents;
import com.ctip.domain.event.TenantEvents;
import com.ctip.domain.event.ThreatEvents;
import com.ctip.domain.event.UserEvents;
import com.ctip.domain.event.WebhookEvents;
import java.util.List;

/**
 * 六個 topic 與 domain event 的對應(docs/spec/13-platform-ops.md §13.1)。
 * 命名格式 {@code ctip.<domain>.<event>.v<schema-version>};對照表同步寫入
 * {@code docs/api/events/README.md},兩者由 {@code EventSchemaContractTest} 綁在一起。
 *
 * <p>每個 domain event 只進<strong>一個</strong>領域 topic。通知形狀的投影另外進
 * {@link #NOTIFICATION_EVENTS},那是消費端(通知扇出)的輸入,不是第二份原始事件。
 */
public final class KafkaTopics {

    /** 攝取生命週期。 */
    public static final String THREAT_INGEST = "ctip.threat.ingest.v1";

    /** Threat 聚合的變更(正規化後的威脅實體)。 */
    public static final String THREAT_NORMALIZED = "ctip.threat.normalized.v1";

    /** Indicator 聚合的全部事件。 */
    public static final String INDICATOR_UPDATED = "ctip.indicator.updated.v1";

    /** 身分、租戶、API key、訂閱、webhook 停用——Phase 21 的稽核消費端由此讀取。 */
    public static final String AUDIT_EVENTS = "ctip.audit.events.v1";

    /** 來源健康。 */
    public static final String SYSTEM_ALERT = "ctip.system.alert.v1";

    /** 通知形狀的投影({@code NotificationEvent});本平台唯一有消費端的 topic。 */
    public static final String NOTIFICATION_EVENTS = "ctip.notification.events.v1";

    public static final List<String> ALL = List.of(
            THREAT_INGEST, THREAT_NORMALIZED, INDICATOR_UPDATED, AUDIT_EVENTS, SYSTEM_ALERT, NOTIFICATION_EVENTS);

    private KafkaTopics() {}

    /**
     * 事件所屬的領域 topic。
     *
     * <p>{@code switch} 而非查表:新增 domain event 時編譯器不會提醒,但 code review 與
     * {@code EventSchemaContractTest}(比對 §2.4 的事件清單)會。
     */
    public static String of(DomainEvent event) {
        return switch (event) {
            case IndicatorEvents.IndicatorCreated ignored -> INDICATOR_UPDATED;
            case IndicatorEvents.IndicatorMerged ignored -> INDICATOR_UPDATED;
            case IndicatorEvents.IndicatorExpired ignored -> INDICATOR_UPDATED;
            case IndicatorEvents.IndicatorRevoked ignored -> INDICATOR_UPDATED;
            case IndicatorEvents.IndicatorFalsePositiveReported ignored -> INDICATOR_UPDATED;
            case IndicatorEvents.IndicatorTlpTightened ignored -> INDICATOR_UPDATED;
            case ThreatEvents.ThreatUpdated ignored -> THREAT_NORMALIZED;
            case IngestionEvents.IngestionStarted ignored -> THREAT_INGEST;
            case IngestionEvents.IngestionCompleted ignored -> THREAT_INGEST;
            case IngestionEvents.IngestionFailed ignored -> THREAT_INGEST;
            case SourceEvents.SourceDegraded ignored -> SYSTEM_ALERT;
            case SourceEvents.SourceFailed ignored -> SYSTEM_ALERT;
            case SourceEvents.SourceRecovered ignored -> SYSTEM_ALERT;
            case TenantEvents.TenantCreated ignored -> AUDIT_EVENTS;
            case UserEvents.UserRegistered ignored -> AUDIT_EVENTS;
            case UserEvents.TokenReuseDetected ignored -> AUDIT_EVENTS;
            case ApiKeyEvents.ApiKeyCreated ignored -> AUDIT_EVENTS;
            case ApiKeyEvents.ApiKeyRevoked ignored -> AUDIT_EVENTS;
            case SubscriptionEvents.SubscriptionChanged ignored -> AUDIT_EVENTS;
            case BloomEvents.BloomSnapshotReady ignored -> SYSTEM_ALERT;
            case WebhookEvents.WebhookDisabled ignored -> AUDIT_EVENTS;
            default ->
                throw new IllegalArgumentException("沒有為 " + event.eventType()
                        + " 指定 topic;新增 domain event 時必須同時更新本對應表與 docs/api/events/README.md");
        };
    }
}
