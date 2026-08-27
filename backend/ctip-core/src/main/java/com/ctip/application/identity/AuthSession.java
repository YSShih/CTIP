package com.ctip.application.identity;

/**
 * 登入／輪替後回傳給呼叫端的一組憑證。{@code refreshToken} 為原文,只在此回傳,
 * 資料庫只存其 SHA-256 雜湊(§10.4)。{@code displayName} 僅供 UI 顯示,不進 JWT claims。
 */
public record AuthSession(
        String accessToken,
        String refreshToken,
        long expiresInSeconds,
        AuthenticatedIdentity identity,
        String displayName) {

    public AuthSession withDisplayName(String name) {
        return new AuthSession(accessToken, refreshToken, expiresInSeconds, identity, name);
    }
}
