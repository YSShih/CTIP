package com.ctip.domain.user;

import java.util.List;

/**
 * 輪替結果。{@code mutated} 是狀態有變動、必須持久化的既有 token;
 * {@code issued} 僅在 {@link RefreshTokenRotationOutcome#ROTATED} 時非 null。
 */
public record RefreshTokenRotation(
        RefreshTokenRotationOutcome outcome, RefreshToken issued, List<RefreshToken> mutated) {

    public RefreshTokenRotation {
        mutated = List.copyOf(mutated);
    }

    public boolean isRotated() {
        return outcome == RefreshTokenRotationOutcome.ROTATED;
    }
}
