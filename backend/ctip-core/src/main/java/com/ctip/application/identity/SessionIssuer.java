package com.ctip.application.identity;

import com.ctip.application.port.AccessTokenClaims;
import com.ctip.application.port.AccessTokenPort;
import com.ctip.application.port.IdGeneratorPort;
import com.ctip.application.port.RefreshTokenRepository;
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

    /** 新登入:開一個新的輪替家族,建立並持久化第一枚 refresh token。 */
    public AuthSession issueNewSession(AuthenticatedIdentity identity, ClientInfo client) {
        IssuedRefreshToken issued = refreshTokens.create(identity.userId(), refreshTokens.newFamily(), null, client);
        refreshTokenRepository.save(issued.token());
        return sign(identity, issued);
    }

    /**
     * 輪替後續簽:<strong>不再寫入</strong>——新枚已由 {@link RefreshTokenRotator} 在「消耗舊枚」的
     * 同一個交易內持久化。若在這裡才存,舊枚已提交為已使用、新枚卻可能存不進去,
     * 使用者會在一次基礎設施抖動後被無聲登出且該 family 就此斷掉。
     */
    public AuthSession resume(AuthenticatedIdentity identity, IssuedRefreshToken issued) {
        return sign(identity, issued);
    }

    private AuthSession sign(AuthenticatedIdentity identity, IssuedRefreshToken issued) {
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
