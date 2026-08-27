package com.ctip.application.identity;

import com.ctip.domain.user.RefreshTokenRotationOutcome;
import com.ctip.domain.user.User;

/**
 * 輪替結果。與 {@link LoginResult} 同理,失敗<strong>以回傳值表達</strong>——
 * 重用偵測必須把整個 family 的撤銷寫入資料庫,若在同一交易內丟例外,撤銷會被 rollback,
 * 不變量 U5 形同失效(ADR 0012 決策 9)。
 */
public record RotatedTokens(RefreshTokenRotationOutcome outcome, User user, IssuedRefreshToken issued) {

    public static RotatedTokens rotated(User user, IssuedRefreshToken issued) {
        return new RotatedTokens(RefreshTokenRotationOutcome.ROTATED, user, issued);
    }

    public static RotatedTokens failed(RefreshTokenRotationOutcome outcome) {
        return new RotatedTokens(outcome, null, null);
    }

    public boolean isRotated() {
        return outcome == RefreshTokenRotationOutcome.ROTATED;
    }
}
