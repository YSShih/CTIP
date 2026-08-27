package com.ctip.application.identity;

import com.ctip.domain.user.User;

/**
 * 登入結果。失敗<strong>以回傳值而非例外</strong>表達——失敗計數(不變量 U7)必須隨交易提交,
 * 若在同一個 {@code @Transactional} 內丟例外,計數會連同交易一起 rollback,鎖定機制形同失效
 * (ADR 0012 決策 9)。
 */
public record LoginResult(User user, LoginFailure failure) {

    public static LoginResult success(User user) {
        return new LoginResult(user, null);
    }

    public static LoginResult failed(LoginFailure failure) {
        return new LoginResult(null, failure);
    }

    public boolean isSuccess() {
        return failure == null;
    }
}
