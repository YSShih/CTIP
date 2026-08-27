package com.ctip.application.port;

/**
 * Token 驗證結果。EXPIRED 與 INVALID 必須可區分——§9.4 對前者回 {@code TOKEN_EXPIRED}、
 * 後者回 {@code UNAUTHENTICATED}(安全測試條號 4)。
 */
public record AccessTokenVerification(Status status, AccessTokenClaims claims) {

    public enum Status {
        VALID,
        EXPIRED,
        INVALID
    }

    public static AccessTokenVerification valid(AccessTokenClaims claims) {
        return new AccessTokenVerification(Status.VALID, claims);
    }

    public static AccessTokenVerification expired() {
        return new AccessTokenVerification(Status.EXPIRED, null);
    }

    public static AccessTokenVerification invalid() {
        return new AccessTokenVerification(Status.INVALID, null);
    }
}
