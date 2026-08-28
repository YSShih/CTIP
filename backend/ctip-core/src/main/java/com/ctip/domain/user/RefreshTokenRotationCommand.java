package com.ctip.domain.user;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 輪替所需的全部輸入。{@code replacement} 由 application 層以
 * IdGeneratorPort / ClockPort 預先建構——domain 不得取時間或亂數(ArchUnit 規則 9,
 * 該規則亦禁止 domain 出現名為 {@code now} 的方法,故時間欄位命名為 {@code at})。
 */
public record RefreshTokenRotationCommand(
        RefreshToken presented,
        List<RefreshToken> family,
        RefreshToken replacement,
        Instant at,
        Duration familyMaxLifetime) {

    public RefreshTokenRotationCommand {
        List.copyOf(family);
    }
}
