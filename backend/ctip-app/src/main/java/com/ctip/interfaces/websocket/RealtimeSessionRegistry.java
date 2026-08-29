package com.ctip.interfaces.websocket;

import com.ctip.application.notification.NotificationRecord;
import com.ctip.application.port.RealtimePushPort;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 目前連上的即時推送連線(09 §9.1)。WebSocket 與 SSE 共用同一份登記簿與同一條過濾規則。
 *
 * <p><strong>過濾在伺服器端</strong>:平台範圍的通知(public tenant)推給所有連線,
 * 租戶自有的只推給該租戶;指名使用者的通知只推給本人。這與
 * {@code GET /notifications} 的可見範圍是同一條規則,兩邊不得分歧。
 *
 * <p>推播是<strong>盡力而為</strong>:沒有連線就什麼都不做。通知在 dispatch 的第一步就已落庫,
 * 通知中心頁一定看得到——推播失敗不得回頭影響任何東西。
 *
 * <p>M1–M3 為單一實例,登記簿在記憶體即可(08 §8.7 的同一個前提)。多實例需要共用的
 * pub/sub,擴充點就是這個介面。
 */
@Component
class RealtimeSessionRegistry implements RealtimePushPort {

    private static final Logger log = LoggerFactory.getLogger(RealtimeSessionRegistry.class);

    /** 09 §9.1:WS 每 30s ping、SSE 每 30s 送 keepalive 註解行。 */
    static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(30);

    private final Map<String, RealtimeSubscriber> subscribers = new ConcurrentHashMap<>();
    private final RealtimeMessageCodec codec;

    /**
     * 心跳<strong>不走 {@code @Scheduled}</strong>:那整套受 {@code SCHEDULER_ENABLED} 控制
     * (測試環境一律關閉),而連線保活與業務排程是兩件事——沒有心跳的連線會被中間的反向代理
     * 在閒置逾時後直接切掉,那不是可以「在測試環境關掉」的東西。
     */
    private final ScheduledExecutorService heartbeats = Executors.newSingleThreadScheduledExecutor(
            Thread.ofVirtual().name("ctip-realtime-heartbeat").factory());

    RealtimeSessionRegistry(RealtimeMessageCodec codec) {
        this.codec = codec;
        heartbeats.scheduleAtFixedRate(
                this::sendHeartbeats, HEARTBEAT_INTERVAL.toSeconds(), HEARTBEAT_INTERVAL.toSeconds(), TimeUnit.SECONDS);
    }

    void register(RealtimeSubscriber subscriber) {
        subscribers.put(subscriber.id(), subscriber);
    }

    void unregisterSession(String id) {
        subscribers.remove(id);
    }

    int size() {
        return subscribers.size();
    }

    void sendHeartbeats() {
        for (RealtimeSubscriber subscriber : subscribers.values()) {
            try {
                if (!subscriber.heartbeat()) {
                    subscribers.remove(subscriber.id());
                }
            } catch (RuntimeException e) {
                log.debug("心跳失敗,移除連線", e);
                subscribers.remove(subscriber.id());
            }
        }
    }

    @PreDestroy
    void shutdown() {
        heartbeats.shutdownNow();
        subscribers.values().forEach(RealtimeSubscriber::close);
        subscribers.clear();
    }

    @Override
    public void push(NotificationRecord notification) {
        String message = codec.encode(notification);
        for (RealtimeSubscriber subscriber : subscribers.values()) {
            if (!isVisibleTo(notification, subscriber)) {
                continue;
            }
            try {
                if (!subscriber.deliver(message)) {
                    subscribers.remove(subscriber.id());
                }
            } catch (RuntimeException e) {
                log.debug("推播失敗,移除該連線", e);
                subscribers.remove(subscriber.id());
            }
        }
    }

    /** 與 {@code NotificationAdapter} 的 {@code IN (current, public)} 同一條規則。 */
    private static boolean isVisibleTo(NotificationRecord notification, RealtimeSubscriber subscriber) {
        boolean tenantMatches =
                notification.tenantId().isPublic() || notification.tenantId().equals(subscriber.tenantId());
        boolean userMatches =
                notification.userId() == null || notification.userId().equals(subscriber.userId());
        return tenantMatches && userMatches;
    }
}
