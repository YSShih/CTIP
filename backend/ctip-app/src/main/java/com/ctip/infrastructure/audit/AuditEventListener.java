package com.ctip.infrastructure.audit;

import com.ctip.application.audit.AuditEvent;
import com.ctip.application.port.AuditPort;
import com.ctip.domain.audit.AuditAction;
import com.ctip.domain.audit.AuditActorType;
import com.ctip.domain.audit.AuditResult;
import com.ctip.domain.event.ApiKeyEvents;
import com.ctip.domain.event.DomainEvent;
import com.ctip.domain.event.IngestionEvents;
import com.ctip.domain.event.SubscriptionEvents;
import com.ctip.domain.event.TenantEvents;
import com.ctip.domain.event.UserEvents;
import com.ctip.domain.event.WebhookEvents;
import com.ctip.infrastructure.event.DomainEventEnvelope;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.event.EventListener;

/**
 * 以 domain event 為觸發點的 9 種稽核行為(docs/spec/13-platform-ops.md §13.5 觸發點對照表);
 * 其餘 17 種以請求為觸發點,見 {@link AuditAccessFilter}。
 *
 * <p>接的是<strong>程序內</strong>的 {@code DomainEventEnvelope} 而不是
 * {@code ctip.audit.events.v1} 的 Kafka 消費端:轉發到 Kafka 是**額外**的一個消費端
 * (§13.1「發佈端程式碼永不修改」),而 mvp／dev 根本沒有 broker——稽核不能只在
 * staging/prod 才寫得出來。同一個信封在兩種傳輸下都會抵達這裡,且只抵達一次。
 *
 * <p>{@code SpringEventPublisherAdapter} 已在 AFTER_COMMIT 才發佈,因此這裡看到的事實都已提交
 * (與 {@code KafkaEventForwarder} 同一個判斷)。
 */
public class AuditEventListener {

    private final AuditPort audit;

    public AuditEventListener(AuditPort audit) {
        this.audit = audit;
    }

    @EventListener
    public void onDomainEvent(DomainEventEnvelope envelope) {
        auditOf(envelope.event()).ifPresent(audit::record);
    }

    private static Optional<AuditEvent> auditOf(DomainEvent event) {
        return identityOf(event).or(() -> platformOf(event));
    }

    private static Optional<AuditEvent> identityOf(DomainEvent event) {
        return switch (event) {
            case TenantEvents.TenantCreated e ->
                system(AuditAction.TENANT_CREATED, e, "tenant", e.tenantId().value(), Map.of("slug", e.slug()));
            case UserEvents.UserRegistered e ->
                system(AuditAction.USER_CREATED, e, "user", e.userId().value(), Map.of());
            case UserEvents.TokenReuseDetected e ->
                denied(AuditAction.TOKEN_REUSE_DETECTED, e, e.userId().value());
            case ApiKeyEvents.ApiKeyCreated e ->
                actor(AuditAction.API_KEY_CREATED, e, "api_key", e.apiKeyId().value());
            case ApiKeyEvents.ApiKeyRevoked e ->
                actor(AuditAction.API_KEY_REVOKED, e, "api_key", e.apiKeyId().value());
            default -> Optional.empty();
        };
    }

    private static Optional<AuditEvent> platformOf(DomainEvent event) {
        return switch (event) {
            case SubscriptionEvents.SubscriptionChanged e ->
                system(
                        AuditAction.SUBSCRIPTION_CHANGED,
                        e,
                        "subscription",
                        e.subscriptionId().value(),
                        Map.of(
                                "previousPlan",
                                e.previousPlan().name(),
                                "newPlan",
                                e.newPlan().name(),
                                "status",
                                e.status().name()));
            // 自動停用也是 WEBHOOK_DELETED(§13.5:「以及 Webhook 因連續失敗被自動 DISABLED」)
            case WebhookEvents.WebhookDisabled e ->
                system(
                        AuditAction.WEBHOOK_DELETED,
                        e,
                        "webhook",
                        e.webhookId().value(),
                        Map.of("reason", "consecutive_failures", "failures", e.consecutiveFailures()));
            case IngestionEvents.IngestionStarted e ->
                ingestion(AuditAction.INGESTION_STARTED, e, e.sourceId().value(), AuditResult.SUCCESS);
            case IngestionEvents.IngestionCompleted e ->
                ingestion(AuditAction.INGESTION_COMPLETED, e, e.sourceId().value(), AuditResult.SUCCESS);
            case IngestionEvents.IngestionFailed e ->
                ingestion(AuditAction.INGESTION_FAILED, e, e.sourceId().value(), AuditResult.FAILURE);
            default -> Optional.empty();
        };
    }

    private static Optional<AuditEvent> ingestion(
            AuditAction action, DomainEvent event, UUID sourceId, AuditResult result) {
        return Optional.of(AuditEvent.system(action, result, event.tenantId()).withResource("source", sourceId));
    }

    private static Optional<AuditEvent> system(
            AuditAction action, DomainEvent event, String resourceType, UUID resourceId, Map<String, Object> data) {
        return Optional.of(AuditEvent.system(action, AuditResult.SUCCESS, event.tenantId())
                .withResource(resourceType, resourceId)
                .withMetadata(data));
    }

    /** 由使用者操作觸發的事件:行為者取自當下的請求上下文(事件在 AFTER_COMMIT 抵達,仍在該請求內)。 */
    private static Optional<AuditEvent> actor(
            AuditAction action, DomainEvent event, String resourceType, UUID resourceId) {
        return Optional.of(AuditEvent.of(action, AuditResult.SUCCESS)
                .withTenant(event.tenantId())
                .withResource(resourceType, resourceId));
    }

    private static Optional<AuditEvent> denied(AuditAction action, DomainEvent event, UUID userId) {
        return Optional.of(AuditEvent.system(action, AuditResult.DENIED, event.tenantId())
                .withActor(AuditActorType.USER, userId)
                .withResource("user", userId));
    }
}
