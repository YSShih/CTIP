package com.ctip.application.identity;

/**
 * 認證失敗。訊息刻意不區分「帳號不存在」與「密碼錯誤」——避免帳號列舉
 * (docs/spec/13-platform-ops.md 安全要求)。
 */
public class AuthenticationFailedException extends RuntimeException {

    @java.io.Serial
    private static final long serialVersionUID = 1L;

    public AuthenticationFailedException(String message) {
        super(message);
    }
}
