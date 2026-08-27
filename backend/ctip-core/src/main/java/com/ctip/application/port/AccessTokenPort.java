package com.ctip.application.port;

/** JWT 簽發與驗證(HS256,§10.4)。實作在 infrastructure,core 不得依賴 JOSE 函式庫。 */
public interface AccessTokenPort {

    String issue(AccessTokenClaims claims);

    AccessTokenVerification verify(String token);

    /** access token 的存活秒數,供 API 回應的 {@code expiresIn}。 */
    long accessTokenTtlSeconds();
}
