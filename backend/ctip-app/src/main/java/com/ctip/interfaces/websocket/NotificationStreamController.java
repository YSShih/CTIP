package com.ctip.interfaces.websocket;

import com.ctip.application.identity.AuthenticatedIdentity;
import com.ctip.application.plan.QuotaService;
import com.ctip.infrastructure.security.TenantContext;
import com.ctip.interfaces.rest.openapi.RealtimeApi;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE fallback(09 §9.1「即時推送」:{@code GET /api/v1/events})。
 *
 * <p>與 WebSocket 端點<strong>共用同一個方案閘門</strong>({@code plans.websocket_enabled}):
 * §9.1 只在 WebSocket 那一列寫了授權要求,但只擋 WebSocket 等於任何 client 改連
 * {@code /events} 就繞過方案限制——兩者是同一個能力的兩種傳輸(ADR 0029)。
 */
@RestController
class NotificationStreamController implements RealtimeApi {

    private final RealtimeStreams streams;
    private final QuotaService quotas;
    private final TenantContext tenantContext;

    NotificationStreamController(RealtimeStreams streams, QuotaService quotas, TenantContext tenantContext) {
        this.streams = streams;
        this.quotas = quotas;
        this.tenantContext = tenantContext;
    }

    @Override
    @GetMapping(value = "/api/v1/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority('notification:read')")
    public SseEmitter subscribe() {
        AuthenticatedIdentity caller = tenantContext.requireIdentity();
        quotas.requireRealtimePush(caller.tenantId());
        return streams.open(caller);
    }
}
