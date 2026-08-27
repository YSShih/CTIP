package com.ctip.application.identity;

import java.time.Duration;

/** 不變量 U7 的門檻(§10.4:連續失敗 10 次 → 鎖定 15 分鐘)。 */
public record LoginPolicy(int maxFailedAttempts, Duration lockDuration) {

    public LoginPolicy {
        if (maxFailedAttempts <= 0) {
            throw new IllegalArgumentException("maxFailedAttempts 必須為正");
        }
        if (lockDuration == null || lockDuration.isZero() || lockDuration.isNegative()) {
            throw new IllegalArgumentException("lockDuration 必須為正");
        }
    }
}
