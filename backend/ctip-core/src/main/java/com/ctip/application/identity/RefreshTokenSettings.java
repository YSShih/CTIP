package com.ctip.application.identity;

import java.time.Duration;

/** Refresh token 存活時間(JWT_REFRESH_TOKEN_EXPIRATION,預設 30 天;§10.4)。 */
public record RefreshTokenSettings(Duration ttl) {

    public RefreshTokenSettings {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("refresh token ttl 必須為正");
        }
    }
}
