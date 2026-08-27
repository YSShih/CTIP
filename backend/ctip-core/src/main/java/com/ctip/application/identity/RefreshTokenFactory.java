package com.ctip.application.identity;

import com.ctip.application.port.ClockPort;
import com.ctip.application.port.IdGeneratorPort;
import com.ctip.application.port.SecureTokenGeneratorPort;
import com.ctip.domain.user.RefreshToken;
import com.ctip.domain.user.RefreshTokenId;
import com.ctip.domain.user.RefreshTokenSnapshot;
import com.ctip.domain.user.TokenFamilyId;
import com.ctip.domain.user.TokenHash;
import com.ctip.domain.user.UserId;
import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * 簽發 refresh token:原文以 SecureRandom 產生並僅回傳一次,持久化的實體只帶 SHA-256 雜湊
 * (docs/spec/10-identity-plans.md §10.4)。
 */
@Service
public class RefreshTokenFactory {

    private static final int TOKEN_LENGTH = 48;

    private final SecureTokenGeneratorPort tokenGenerator;
    private final IdGeneratorPort idGenerator;
    private final ClockPort clock;
    private final RefreshTokenSettings settings;

    public RefreshTokenFactory(
            SecureTokenGeneratorPort tokenGenerator,
            IdGeneratorPort idGenerator,
            ClockPort clock,
            RefreshTokenSettings settings) {
        this.tokenGenerator = tokenGenerator;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.settings = settings;
    }

    /** {@code parentId} 為 null 表示 family 的第一枚(登入);輪替時沿用同一 {@code family}。 */
    public IssuedRefreshToken create(UserId userId, TokenFamilyId family, RefreshTokenId parentId, ClientInfo client) {
        String plaintext = tokenGenerator.randomBase62(TOKEN_LENGTH);
        Instant now = clock.now();
        RefreshTokenSnapshot snapshot = new RefreshTokenSnapshot(
                new RefreshTokenId(idGenerator.nextId()),
                userId,
                TokenHash.of(plaintext),
                family,
                parentId,
                now,
                now.plus(settings.ttl()),
                null,
                null,
                null,
                client.userAgent(),
                client.ip());
        return new IssuedRefreshToken(RefreshToken.issue(snapshot), plaintext);
    }

    public TokenFamilyId newFamily() {
        return new TokenFamilyId(idGenerator.nextId());
    }
}
