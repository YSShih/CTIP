package com.ctip.interfaces.websocket;

import com.ctip.application.identity.AuthenticatedIdentity;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 建立一條 SSE 連線並登記到與 WebSocket 共用的登記簿。
 * 讓 controller 不必知道 {@link RealtimeSessionRegistry} 的內部型別。
 */
@Component
public class RealtimeStreams {

    /**
     * SSE 連線的存活上限。{@code 0L} 之類的「永不逾時」會讓每條斷掉但伺服器還不知道的連線
     * 永久佔住一個 async request;前端本來就會自動重連。
     */
    private static final long TIMEOUT_MILLIS = 30 * 60 * 1000L;

    private final RealtimeSessionRegistry registry;

    RealtimeStreams(RealtimeSessionRegistry registry) {
        this.registry = registry;
    }

    public SseEmitter open(AuthenticatedIdentity identity) {
        String id = "sse-" + UUID.randomUUID();
        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
        emitter.onCompletion(() -> registry.unregisterSession(id));
        emitter.onTimeout(() -> registry.unregisterSession(id));
        emitter.onError(error -> registry.unregisterSession(id));
        SseSubscriber subscriber = new SseSubscriber(id, emitter, identity);
        registry.register(subscriber);
        // 立刻送一行註解:回應標頭要等到第一次寫入才會 flush,不送的話 client(與中間的
        // 反向代理)在第一則通知抵達之前都不知道連線已經建立。實測:curl 會一直等到逾時
        subscriber.heartbeat();
        return emitter;
    }
}
