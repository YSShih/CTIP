package com.ctip.application.identity;

/** {@link AuthService} 的輸入載體。{@code userAgent}/{@code ip} 供 refresh token 稽核欄位。 */
public interface AuthCommands {

    record Register(String email, String password, String displayName, String tenantName) {}

    record Login(String email, String password, String userAgent, String ip) {}

    record Refresh(String refreshToken, String userAgent, String ip) {}
}
