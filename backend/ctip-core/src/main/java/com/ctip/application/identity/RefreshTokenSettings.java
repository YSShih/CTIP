package com.ctip.application.identity;

import java.time.Duration;

/**
 * Refresh token 存活時間(JWT_REFRESH_TOKEN_EXPIRATION,預設 30 天;§10.4)。
 *
 * <p>{@code familyMaxLifetime} 是<strong>整個輪替家族</strong>的絕對上限。每次輪替都給滿 ttl,
 * 若沒有這道上限,竊得一枚 refresh token 的人只要每 30 天輪替一次就能無限期維持存取,
 * 而重用偵測只在「兩邊都用同一枚」時才觸發 —— 安靜獨占輪替鏈不會被抓到。
 * §10.4 未定義此規則,依 §0.4(安全性優先)取 90 天(ADR 0013)。
 */
public record RefreshTokenSettings(Duration ttl, Duration familyMaxLifetime) {

    public RefreshTokenSettings {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("refresh token ttl 必須為正");
        }
        if (familyMaxLifetime == null || familyMaxLifetime.compareTo(ttl) < 0) {
            throw new IllegalArgumentException("family 絕對上限不得短於單枚 ttl");
        }
    }
}
