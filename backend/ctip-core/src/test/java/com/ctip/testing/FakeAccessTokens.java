package com.ctip.testing;

import com.ctip.application.port.AccessTokenClaims;
import com.ctip.application.port.AccessTokenPort;
import com.ctip.application.port.AccessTokenVerification;
import java.util.LinkedHashMap;
import java.util.Map;

/** 測試用 access token:以序號字串代表簽章結果,並保留 claims 供斷言。 */
public final class FakeAccessTokens implements AccessTokenPort {

    private final Map<String, AccessTokenClaims> issued = new LinkedHashMap<>();
    private int counter;

    @Override
    public String issue(AccessTokenClaims claims) {
        String token = "access-token-" + (++counter);
        issued.put(token, claims);
        return token;
    }

    @Override
    public AccessTokenVerification verify(String token) {
        AccessTokenClaims claims = issued.get(token);
        return claims == null ? AccessTokenVerification.invalid() : AccessTokenVerification.valid(claims);
    }

    @Override
    public long accessTokenTtlSeconds() {
        return 900;
    }

    public AccessTokenClaims claimsOf(String token) {
        return issued.get(token);
    }
}
