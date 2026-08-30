package com.ctip.infrastructure.audit;

import com.ctip.application.audit.AuditEvent;
import com.ctip.application.audit.AuditRecord;
import com.ctip.application.identity.AuthenticatedIdentity;
import com.ctip.domain.audit.AuditActorType;
import com.ctip.domain.tenant.TenantId;
import com.ctip.infrastructure.observability.TraceIdFilter;
import com.ctip.infrastructure.security.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 把呼叫端交出的 {@link AuditEvent} 補成一整列 {@link AuditRecord}:
 * 補上 id、時間、以及請求上下文(ip / user-agent / traceId / 行為者 / 租戶)。
 *
 * <p><strong>必須在呼叫端的執行緒上執行</strong>——請求範圍的 bean 與 MDC 在稽核寫入執行緒上
 * 都已經不存在。{@code AuditWriter} 因此先在這裡物化,才把成品丟進佇列。
 */
@Component
public class AuditContext {

    private static final int USER_AGENT_MAX_LENGTH = 512;

    private final ObjectProvider<TenantContext> tenantContext;

    public AuditContext(ObjectProvider<TenantContext> tenantContext) {
        this.tenantContext = tenantContext;
    }

    public AuditRecord materialize(AuditEvent event, UUID id, Instant occurredAt) {
        Optional<HttpServletRequest> request = currentRequest();
        Actor actor = actorOf(event, currentIdentity(request.isPresent()));
        return new AuditRecord(
                id,
                occurredAt,
                actor.type(),
                actor.id(),
                actor.tenantId(),
                event.action(),
                event.resourceType(),
                event.resourceId(),
                request.map(AuditClientIp::of).orElse(null),
                request.map(AuditContext::userAgent).orElse(null),
                event.result(),
                MDC.get(TraceIdFilter.MDC_KEY),
                event.metadata());
    }

    /**
     * 行為者的三個欄位一起決定,優先序:呼叫端明說 &gt; 已認證身分 &gt; handler 交出的訊號
     * ({@code /auth/*} 是匿名端點,登入成功之後才知道是誰)&gt; 匿名 / public tenant。
     * 租戶為 NOT NULL,故最後一層一律落在 public tenant。
     */
    private static Actor actorOf(AuditEvent event, Optional<AuthenticatedIdentity> identity) {
        Actor resolved = identity.map(Actor::of)
                .or(() -> AuditSignals.currentActor().map(Actor::of))
                .orElseGet(() -> new Actor(AuditActorType.ANONYMOUS, null, TenantId.PUBLIC));
        return new Actor(
                event.actorType() == null ? resolved.type() : event.actorType(),
                event.actorId() == null ? resolved.id() : event.actorId(),
                event.tenantId() == null ? resolved.tenantId() : event.tenantId());
    }

    private record Actor(AuditActorType type, UUID id, TenantId tenantId) {

        static Actor of(AuthenticatedIdentity caller) {
            boolean apiKey = caller.isApiKey();
            UUID id = apiKey
                    ? caller.apiKeyId().value()
                    : (caller.userId() == null ? null : caller.userId().value());
            return new Actor(apiKey ? AuditActorType.API_KEY : AuditActorType.USER, id, caller.tenantId());
        }

        static Actor of(AuditSignals.Actor signal) {
            return new Actor(signal.type(), signal.actorId(), signal.tenantId());
        }
    }

    private Optional<AuthenticatedIdentity> currentIdentity(boolean inRequest) {
        if (!inRequest) {
            return Optional.empty();
        }
        TenantContext context = tenantContext.getIfAvailable();
        return context == null ? Optional.empty() : context.identity();
    }

    private static Optional<HttpServletRequest> currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? Optional.of(attributes.getRequest())
                : Optional.empty();
    }

    private static String userAgent(HttpServletRequest request) {
        String header = request.getHeader("User-Agent");
        if (header == null) {
            return null;
        }
        return header.length() > USER_AGENT_MAX_LENGTH ? header.substring(0, USER_AGENT_MAX_LENGTH) : header;
    }
}
