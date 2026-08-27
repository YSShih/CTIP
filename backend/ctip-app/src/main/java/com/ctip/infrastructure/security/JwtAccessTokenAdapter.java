package com.ctip.infrastructure.security;

import com.ctip.application.port.AccessTokenClaims;
import com.ctip.application.port.AccessTokenPort;
import com.ctip.application.port.AccessTokenVerification;
import com.ctip.application.port.ClockPort;
import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.user.UserId;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * HS256 JWT 簽發與驗證(docs/spec/10-identity-plans.md §10.4)。
 * claims 僅 sub / tid / roles / perms / iat / exp / jti——<strong>不放 email、姓名或任何個資</strong>。
 * access token 短命,不做黑名單。
 */
public class JwtAccessTokenAdapter implements AccessTokenPort {

    /** HS256 的金鑰下限;§10.4 亦要求 JWT_SECRET ≥ 32 bytes。 */
    private static final int MIN_SECRET_BYTES = 32;

    private final byte[] secret;
    private final Duration accessTtl;
    private final ClockPort clock;

    public JwtAccessTokenAdapter(String secret, Duration accessTtl, ClockPort clock) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        if (this.secret.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET 長度必須 >= " + MIN_SECRET_BYTES + " bytes(HS256 要求)");
        }
        this.accessTtl = accessTtl;
        this.clock = clock;
    }

    @Override
    public String issue(AccessTokenClaims claims) {
        Instant now = clock.now();
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject(claims.userId().value().toString())
                .claim("tid", claims.tenantId().value().toString())
                .claim("roles", List.copyOf(claims.roles()))
                .claim("perms", List.copyOf(claims.permissions()))
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(accessTtl)))
                .jwtID(claims.jti().toString())
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claimsSet);
        try {
            jwt.sign(new MACSigner(secret));
        } catch (JOSEException e) {
            throw new IllegalStateException("JWT 簽章失敗", e);
        }
        return jwt.serialize();
    }

    @Override
    public AccessTokenVerification verify(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!jwt.verify(new MACVerifier(secret))) {
                return AccessTokenVerification.invalid();
            }
            return evaluate(jwt.getJWTClaimsSet());
        } catch (java.text.ParseException | JOSEException | RuntimeException e) {
            return AccessTokenVerification.invalid();
        }
    }

    @Override
    public long accessTokenTtlSeconds() {
        return accessTtl.toSeconds();
    }

    private AccessTokenVerification evaluate(JWTClaimsSet claims) throws java.text.ParseException {
        Date expiration = claims.getExpirationTime();
        if (expiration == null || !expiration.toInstant().isAfter(clock.now())) {
            return AccessTokenVerification.expired();
        }
        return AccessTokenVerification.valid(new AccessTokenClaims(
                new UserId(UUID.fromString(claims.getSubject())),
                new TenantId(UUID.fromString(claims.getStringClaim("tid"))),
                Set.copyOf(claims.getStringListClaim("roles")),
                Set.copyOf(claims.getStringListClaim("perms")),
                UUID.fromString(claims.getJWTID())));
    }
}
