package com.ctip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ctip.application.identity.AuthService;
import com.ctip.application.identity.AuthSession;
import com.ctip.application.notification.NotificationService;
import com.ctip.application.port.ClockPort;
import com.ctip.application.port.IdGeneratorPort;
import com.ctip.application.port.PlanRepository;
import com.ctip.application.port.SubscriptionRepository;
import com.ctip.application.port.TenantMembershipRepository;
import com.ctip.application.rbac.RoleCode;
import com.ctip.domain.plan.PlanCode;
import com.ctip.domain.tenant.TenantId;
import com.ctip.sdk.Severity;
import com.ctip.support.TestIdentities;
import com.ctip.support.TestPlans;
import com.ctip.support.WebhookFixtures;
import com.ctip.support.WebhookTestConfig;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

/**
 * {@code GET /api/v1/ws} 的握手與推送(docs/spec/09-api.md §9.1「即時推送」)。
 *
 * <p>真的起一個伺服器並用原生 WebSocket client 連上去:握手是這條路徑上唯一做授權判斷的地方,
 * 用 mock 驗不出「token 走 {@code Sec-WebSocket-Protocol}」與「方案不足時升級被拒」這兩件事。
 *
 * <p>DoD M3-05 的 Playwright 測的是前端的自動重連;這裡測的是伺服器端。
 */
@Import(WebhookTestConfig.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RealtimePushTest extends AbstractPostgresIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private NotificationService notifications;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthService authService;

    @Autowired
    private TenantMembershipRepository memberships;

    @Autowired
    private PlanRepository plans;

    @Autowired
    private SubscriptionRepository subscriptions;

    @Autowired
    private IdGeneratorPort idGenerator;

    @Autowired
    private ClockPort clock;

    private TestIdentities identities;
    private TestPlans testPlans;

    @BeforeEach
    void setUp() {
        identities = new TestIdentities(authService, memberships);
        testPlans = new TestPlans(plans, subscriptions, idGenerator, clock);
    }

    @Test
    void aTenantReceivesItsOwnNotificationOverTheWebSocket() throws Exception {
        AuthSession session = premium("ws-push");
        BlockingQueue<String> received = new ArrayBlockingQueue<>(8);
        try (WebSocketSession socket = connect(session, received)) {
            UUID eventId = idGenerator.nextId();
            notifications.dispatch(
                    WebhookFixtures.newIoc(eventId, session.identity().tenantId(), Severity.HIGH, Set.of(), Set.of()));

            String message = received.poll(10, TimeUnit.SECONDS);
            assertThat(message).as("十秒內沒有收到推播").isNotNull();
            var node = objectMapper.readTree(message);
            assertThat(node.get("type").asString()).isEqualTo("NEW_IOC");
            assertThat(node.get("eventId").asString()).isEqualTo(eventId.toString());
            assertThat(node.at("/payload/title").asString()).contains("198.51.100.7");
        }
    }

    /** 伺服器端過濾:別的租戶的事件不得推到這條連線上。 */
    @Test
    void anotherTenantsNotificationIsNeverPushed() throws Exception {
        AuthSession listener = premium("ws-listener");
        AuthSession other = premium("ws-other");
        BlockingQueue<String> received = new ArrayBlockingQueue<>(8);
        try (WebSocketSession socket = connect(listener, received)) {
            notifications.dispatch(WebhookFixtures.newIoc(
                    idGenerator.nextId(), other.identity().tenantId(), Severity.HIGH, Set.of(), Set.of()));
            assertThat(received.poll(2, TimeUnit.SECONDS)).isNull();

            // 對照組:平台範圍的事件推得到,證明這條連線是活的
            notifications.dispatch(
                    WebhookFixtures.newIoc(idGenerator.nextId(), TenantId.PUBLIC, Severity.HIGH, Set.of(), Set.of()));
            assertThat(received.poll(10, TimeUnit.SECONDS)).isNotNull();
        }
    }

    /** 沒有 token 的升級請求不得建立連線。 */
    @Test
    void anUnauthenticatedUpgradeIsRejected() {
        assertThatThrownBy(() -> new StandardWebSocketClient()
                        .execute(new TextWebSocketHandler() {}, new WebSocketHttpHeaders(), uri())
                        .get(10, TimeUnit.SECONDS))
                .hasMessageContaining("401");
    }

    /** 方案沒有 websocket_enabled 時升級回 403(09 §9.1 的授權列)。 */
    @Test
    void aPlanWithoutRealtimePushIsRejected() {
        AuthSession free = identities.register("ws-free@example.org", RoleCode.TENANT_ADMIN);
        assertThatThrownBy(() -> new StandardWebSocketClient()
                        .execute(new TextWebSocketHandler() {}, protocols(free), uri())
                        .get(10, TimeUnit.SECONDS))
                .hasMessageContaining("403");
    }

    /** 偽造的 token 不得通過握手。 */
    @Test
    void anInvalidTokenIsRejected() {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.setSecWebSocketProtocol(List.of("ctip.auth", "ctip.auth.not-a-real-token"));
        assertThatThrownBy(() -> new StandardWebSocketClient()
                        .execute(new TextWebSocketHandler() {}, headers, uri())
                        .get(10, TimeUnit.SECONDS))
                .hasMessageContaining("401");
    }

    private WebSocketSession connect(AuthSession session, BlockingQueue<String> sink) throws Exception {
        WebSocketSession socket = new StandardWebSocketClient()
                .execute(
                        new TextWebSocketHandler() {
                            @Override
                            protected void handleTextMessage(WebSocketSession ignored, TextMessage message) {
                                sink.offer(message.getPayload());
                            }
                        },
                        protocols(session),
                        uri())
                .get(10, TimeUnit.SECONDS);
        // 伺服器選的是不帶 token 的子協定——回應標頭會進代理與瀏覽器的 log
        assertThat(socket.getAcceptedProtocol()).isEqualTo("ctip.auth");
        return socket;
    }

    /**
     * 瀏覽器的 WebSocket API 無法設自訂標頭,token 因此走 {@code Sec-WebSocket-Protocol}
     * (09 §9.1;<strong>不接受 query string</strong>,那會進 access log)。
     * 同時提供不帶 token 的 {@code ctip.auth} 讓伺服器有東西可以選。
     */
    private static WebSocketHttpHeaders protocols(AuthSession session) {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.setSecWebSocketProtocol(List.of("ctip.auth", "ctip.auth." + session.accessToken()));
        return headers;
    }

    private URI uri() {
        return URI.create("ws://localhost:" + port + "/api/v1/ws");
    }

    private AuthSession premium(String slug) {
        AuthSession session = identities.register(slug + "@example.org", RoleCode.TENANT_ADMIN);
        testPlans.assign(session.identity().tenantId(), PlanCode.PREMIUM);
        return session;
    }
}
