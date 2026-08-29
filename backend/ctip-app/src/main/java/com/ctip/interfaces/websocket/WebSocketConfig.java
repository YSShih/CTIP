package com.ctip.interfaces.websocket;

import com.ctip.application.plan.QuotaService;
import com.ctip.config.CtipProperties;
import com.ctip.infrastructure.security.AccessTokenIdentityResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * {@code GET /api/v1/ws} 的註冊(09 §9.1)。
 *
 * <p>不設 {@code setAllowedOrigins("*")}:預設只允許同源,跨來源的前端由
 * {@code CORS_ALLOWED_ORIGINS} 明列——WebSocket 不受瀏覽器的 CORS 保護,
 * 放行任意來源等於讓任何網站用使用者的 token 開一條推送通道。
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSocket
class WebSocketConfig implements WebSocketConfigurer {

    private final RealtimeSessionRegistry registry;
    private final AccessTokenIdentityResolver accessTokens;
    private final QuotaService quotas;
    private final String allowedOrigins;

    WebSocketConfig(
            RealtimeSessionRegistry registry,
            AccessTokenIdentityResolver accessTokens,
            QuotaService quotas,
            CtipProperties properties) {
        this.registry = registry;
        this.accessTokens = accessTokens;
        this.quotas = quotas;
        this.allowedOrigins = properties.cors().allowedOrigins();
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new NotificationWebSocketHandler(this.registry), "/api/v1/ws")
                .addInterceptors(new WebSocketAuthInterceptor(accessTokens, quotas))
                .setAllowedOrigins(allowedOrigins.split(","));
    }
}
