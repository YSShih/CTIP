package com.ctip.application.identity;

import com.ctip.application.port.AccessTokenClaims;
import com.ctip.application.port.AccessTokenPort;
import com.ctip.application.port.IdGeneratorPort;
import com.ctip.application.port.RefreshTokenRepository;
import com.ctip.domain.user.RefreshTokenId;
import com.ctip.domain.user.TokenFamilyId;
import java.util.Set;
import org.springframework.stereotype.Service;

/** 組出一組 access + refresh 憑證。access token claims 僅 sub/tid/roles/perms/jti(§10.4)。 */
@Service
public class SessionIssuer {

    private final AccessTokenPort accessTokens;
    private final RefreshTokenFactory refreshTokens;
    private final RefreshTokenRepository refreshTokenRepository;
    private final IdGeneratorPort idGenerator;

    public SessionIssuer(
            AccessTokenPort accessTokens,
            RefreshTokenFactory refreshTokens,
            RefreshTokenRepository refreshTokenRepository,
            IdGeneratorPort idGenerator) {
        this.accessTokens = accessTokens;
        this.refreshTokens = refreshTokens;
        this.refreshTokenRepository = refreshTokenRepository;
        this.idGenerator = idGenerator;
    }

    /** 新登入:開一個新的輪替家族。 */
    public AuthSession issueNewSession(AuthenticatedIdentity identity, ClientInfo client) {
        return issue(identity, refreshTokens.newFamily(), null, client);
    }

    /** 輪替:沿用既有家族並以舊枚為 parent。 */
    public AuthSession issue(
            AuthenticatedIdentity identity, TokenFamilyId family, RefreshTokenId parentId, ClientInfo client) {
        return complete(identity, refreshTokens.create(identity.userId(), family, parentId, client));
    }

    /** 持久化新枚 refresh token 並簽發 access token(輪替路徑已在別處建構好新枚)。 */
    public AuthSession complete(AuthenticatedIdentity identity, IssuedRefreshToken issued) {
        refreshTokenRepository.save(issued.token());
        String accessToken = accessTokens.issue(claimsFor(identity));
        return new AuthSession(accessToken, issued.plaintext(), accessTokens.accessTokenTtlSeconds(), identity, null);
    }

    private AccessTokenClaims claimsFor(AuthenticatedIdentity identity) {
        return new AccessTokenClaims(
                identity.userId(),
                identity.tenantId(),
                Set.of(identity.role().name()),
                identity.permissions(),
                idGenerator.nextId());
    }
}
