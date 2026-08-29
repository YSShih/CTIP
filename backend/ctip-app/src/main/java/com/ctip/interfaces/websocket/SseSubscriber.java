package com.ctip.interfaces.websocket;

import com.ctip.application.identity.AuthenticatedIdentity;
import com.ctip.domain.tenant.TenantId;
import java.io.IOException;
import java.util.UUID;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE fallback 的一條連線(09 §9.1:{@code GET /api/v1/events},{@code text/event-stream})。
 * 訊息格式與 WebSocket 完全相同——SSE 是同一個能力的另一種傳輸,不是另一套協定。
 */
final class SseSubscriber implements RealtimeSubscriber {

    private final String id;
    private final SseEmitter emitter;
    private final AuthenticatedIdentity identity;

    SseSubscriber(String id, SseEmitter emitter, AuthenticatedIdentity identity) {
        this.id = id;
        this.emitter = emitter;
        this.identity = identity;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public TenantId tenantId() {
        return identity.tenantId();
    }

    @Override
    public UUID userId() {
        return identity.userId() == null ? null : identity.userId().value();
    }

    @Override
    public boolean deliver(String message) {
        try {
            emitter.send(SseEmitter.event().name("notification").data(message));
            return true;
        } catch (IOException | IllegalStateException e) {
            return false;
        }
    }

    /** 09 §9.1:SSE 每 30s 送一行 {@code : keepalive} 註解。註解行不觸發 client 的訊息處理。 */
    @Override
    public boolean heartbeat() {
        try {
            emitter.send(SseEmitter.event().comment("keepalive"));
            return true;
        } catch (IOException | IllegalStateException e) {
            return false;
        }
    }

    @Override
    public void close() {
        emitter.complete();
    }
}
