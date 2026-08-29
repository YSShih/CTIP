package com.ctip.interfaces.websocket;

import com.ctip.application.identity.AuthenticatedIdentity;
import com.ctip.domain.tenant.TenantId;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.SubProtocolCapable;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * {@code GET /api/v1/ws} 的處理器(09 §9.1「即時推送」)。
 *
 * <p>原生 WebSocket,<strong>不使用 STOMP／SockJS</strong>:只有一種訊息型態、伺服器→client 單向,
 * 用不到 broker 語意。
 *
 * <p>身分在握手時就決定({@link WebSocketAuthInterceptor}),之後不再接受 client 的任何指令
 * ——收到的 text frame 一律忽略。這條連線沒有「訂閱」動作可下,訂閱範圍就是握手時綁定的租戶。
 */
public class NotificationWebSocketHandler extends TextWebSocketHandler implements SubProtocolCapable {

    /**
     * 握手回應選定的子協定。
     *
     * <p>client 送的是 {@code ctip.auth.<jwt>},但<strong>回應不得把 token 原樣送回</strong>
     * ——回應標頭會進反向代理與瀏覽器的 log。因此 client 同時提供不帶 token 的
     * {@code ctip.auth},伺服器選它;認證資訊只出現在請求方向。
     */
    private static final String SELECTED_SUBPROTOCOL = "ctip.auth";

    private static final Logger log = LoggerFactory.getLogger(NotificationWebSocketHandler.class);

    private final RealtimeSessionRegistry registry;

    public NotificationWebSocketHandler(RealtimeSessionRegistry registry) {
        this.registry = registry;
    }

    @Override
    public List<String> getSubProtocols() {
        return List.of(SELECTED_SUBPROTOCOL);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        AuthenticatedIdentity identity =
                (AuthenticatedIdentity) session.getAttributes().get(WebSocketAuthInterceptor.IDENTITY_ATTRIBUTE);
        if (identity == null) {
            // 握手攔截器沒放身分卻走到這裡,代表裝配有誤;絕不以匿名身分開放推送
            close(session);
            return;
        }
        registry.register(new WebSocketSubscriber(session, identity));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        registry.unregisterSession(session.getId());
    }

    private static void close(WebSocketSession session) {
        try {
            session.close(CloseStatus.SERVER_ERROR);
        } catch (IOException e) {
            log.debug("關閉未認證的 WebSocket 連線失敗", e);
        }
    }

    /** 一條 WebSocket 連線。{@code sendMessage} 非執行緒安全,故同步。 */
    static final class WebSocketSubscriber implements RealtimeSubscriber {

        private final WebSocketSession session;
        private final AuthenticatedIdentity identity;

        WebSocketSubscriber(WebSocketSession session, AuthenticatedIdentity identity) {
            this.session = session;
            this.identity = identity;
        }

        @Override
        public String id() {
            return session.getId();
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
            if (!session.isOpen()) {
                return false;
            }
            synchronized (session) {
                try {
                    session.sendMessage(new TextMessage(message));
                    return true;
                } catch (IOException e) {
                    log.debug("WebSocket 推播失敗,移除連線 {}", session.getId(), e);
                    return false;
                }
            }
        }

        @Override
        public boolean heartbeat() {
            if (!session.isOpen()) {
                return false;
            }
            synchronized (session) {
                try {
                    session.sendMessage(new PingMessage());
                    return true;
                } catch (IOException e) {
                    return false;
                }
            }
        }

        @Override
        public void close() {
            try {
                session.close(CloseStatus.NORMAL);
            } catch (IOException e) {
                log.debug("關閉 WebSocket 連線失敗", e);
            }
        }
    }
}
