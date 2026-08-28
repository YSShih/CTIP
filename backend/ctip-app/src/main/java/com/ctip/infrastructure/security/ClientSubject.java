package com.ctip.infrastructure.security;

import com.ctip.application.identity.AuthenticatedIdentity;
import com.ctip.infrastructure.web.ClientIp;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 「這個請求要記在誰頭上」——節流與限流的呼叫者識別
 * (docs/spec/10-identity-plans.md §10.7 的維度順序:API key → 使用者 → 匿名 IP)。
 *
 * <p>刻意<strong>不</strong>用 tenantId:匿名一律綁在 public tenant 上,以 tenant 記帳等於
 * 全世界的匿名 client 共用一個同步額度——第一個 client 同步完,其他人整天都會拿到 429。
 *
 * <p>前綴不可省:{@code user:} 與 {@code key:} 的 UUID 來自不同的表,
 * 沒有前綴時兩個不同主體理論上可能撞成同一個鍵。
 */
public final class ClientSubject {

    private ClientSubject() {}

    public static String of(TenantContext context, HttpServletRequest request) {
        return context.identity()
                .map(ClientSubject::identify)
                .orElseGet(() -> "ip:" + ClientIp.normalize(request.getRemoteAddr()));
    }

    private static String identify(AuthenticatedIdentity identity) {
        return identity.isApiKey()
                ? "key:" + identity.apiKeyId().value()
                : "user:" + identity.userId().value();
    }
}
