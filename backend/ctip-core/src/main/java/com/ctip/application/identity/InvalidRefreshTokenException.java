package com.ctip.application.identity;

/**
 * Refresh token 無效:不存在、已撤銷、已過期,或偵測到重用(不變量 U5,該 family 已全撤)。
 * 對外一律回 401,不揭露是哪一種。
 */
public class InvalidRefreshTokenException extends RuntimeException {

    @java.io.Serial
    private static final long serialVersionUID = 1L;

    private final boolean reuseDetected;

    public InvalidRefreshTokenException(String message, boolean reuseDetected) {
        super(message);
        this.reuseDetected = reuseDetected;
    }

    public boolean isReuseDetected() {
        return reuseDetected;
    }
}
