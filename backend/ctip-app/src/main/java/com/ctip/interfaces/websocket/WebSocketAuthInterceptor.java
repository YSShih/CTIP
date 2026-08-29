package com.ctip.interfaces.websocket;

import com.ctip.application.identity.AuthenticatedIdentity;
import com.ctip.application.plan.PlanLimitExceededException;
import com.ctip.application.plan.QuotaService;
import com.ctip.infrastructure.security.AccessTokenIdentityResolver;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * WebSocket 握手的認證與授權(09 §9.1「即時推送」)。
 *
 * <p>token 走 {@code Sec-WebSocket-Protocol: ctip.auth.<jwt>}:瀏覽器的 WebSocket API 無法設自訂標頭,
 * 而 <strong>query string 一律不接受</strong>——它會進反向代理與伺服器的 access log。
 *
 * <p>兩道關卡都在<strong>握手</strong>完成,不留任何「已連線但未授權」的中間狀態:
 * <ul>
 *   <li>無效或過期的 token → 401,連線不建立</li>
 *   <li>方案沒有 {@code websocket_enabled} → 403(09 §9.1 的授權列)</li>
 * </ul>
 */
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    static final String IDENTITY_ATTRIBUTE = "ctip.identity";

    private static final String TOKEN_PREFIX = "ctip.auth.";
    private static final Logger log = LoggerFactory.getLogger(WebSocketAuthInterceptor.class);

    private final AccessTokenIdentityResolver accessTokens;
    private final QuotaService quotas;

    public WebSocketAuthInterceptor(AccessTokenIdentityResolver accessTokens, QuotaService quotas) {
        this.accessTokens = accessTokens;
        this.quotas = quotas;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler handler,
            Map<String, Object> attributes) {
        String token = tokenFrom(request.getHeaders());
        if (token == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        AccessTokenIdentityResolver.Resolution resolution = accessTokens.resolve(token);
        if (!resolution.isValid()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        AuthenticatedIdentity identity = resolution.identity();
        if (!identity.permissions().contains("notification:read")) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }
        try {
            quotas.requireRealtimePush(identity.tenantId());
        } catch (PlanLimitExceededException e) {
            log.debug("租戶 {} 的方案未開放即時推送", identity.tenantId().value());
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }
        attributes.put(IDENTITY_ATTRIBUTE, identity);
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler, Exception exception) {
        // 握手之後沒有要做的事;身分已放進 session attributes
    }

    /** {@code Sec-WebSocket-Protocol} 可以是逗號分隔的多個值,取第一個帶 token 的。 */
    private static String tokenFrom(HttpHeaders headers) {
        List<String> offered = headers.get("Sec-WebSocket-Protocol");
        if (offered == null) {
            return null;
        }
        return offered.stream()
                .flatMap(value -> List.of(value.split(",")).stream())
                .map(String::trim)
                .filter(value -> value.startsWith(TOKEN_PREFIX) && value.length() > TOKEN_PREFIX.length())
                .map(value -> value.substring(TOKEN_PREFIX.length()))
                .findFirst()
                .orElse(null);
    }
}
