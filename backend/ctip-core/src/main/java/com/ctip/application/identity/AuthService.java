package com.ctip.application.identity;

import com.ctip.domain.user.RefreshTokenRotationOutcome;
import com.ctip.domain.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 認證流程門面(docs/spec/phases/phase-13.md):register / login / refresh / logout。
 *
 * <p>login / refresh <strong>刻意不標 {@code @Transactional}</strong>:失敗仍必須留下副作用
 * (登入失敗計數 U7、重用偵測的 family 全撤 U5),因此失敗判定由協作者以回傳值交出、
 * 交易在協作者內提交,例外只在交易之外丟出(ADR 0012 決策 9)。
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String INVALID_CREDENTIALS = "Invalid credentials";
    private static final String INVALID_REFRESH_TOKEN = "Invalid refresh token";

    private final UserRegistrar registrar;
    private final LoginAuthenticator loginAuthenticator;
    private final RefreshTokenRotator rotator;
    private final SessionIssuer sessionIssuer;
    private final IdentityResolver identityResolver;

    public AuthService(
            UserRegistrar registrar,
            LoginAuthenticator loginAuthenticator,
            RefreshTokenRotator rotator,
            SessionIssuer sessionIssuer,
            IdentityResolver identityResolver) {
        this.registrar = registrar;
        this.loginAuthenticator = loginAuthenticator;
        this.rotator = rotator;
        this.sessionIssuer = sessionIssuer;
        this.identityResolver = identityResolver;
    }

    @Transactional
    public AuthSession register(AuthCommands.Register command, ClientInfo client) {
        return startSession(registrar.register(command), client);
    }

    /**
     * 全部失敗原因回同一則訊息。原本鎖定與密碼錯誤的訊息可區分 —— 對候選 email 連送 10 次錯密碼,
     * 第 11 次的訊息就分辨出帳號是否存在,直接抵銷 ADR 0012 決策 17 才修掉的時間側信道。
     * 鎖定事實只記伺服器端(ADR 0013)。
     */
    public AuthSession login(AuthCommands.Login command) {
        LoginResult result = loginAuthenticator.authenticate(command);
        if (!result.isSuccess()) {
            if (result.failure() == LoginFailure.LOCKED) {
                log.info("登入被拒:帳號處於鎖定期間(不變量 U7)");
            }
            throw new AuthenticationFailedException(INVALID_CREDENTIALS);
        }
        return startSession(result.user(), new ClientInfo(command.userAgent(), command.ip()));
    }

    public AuthSession refresh(AuthCommands.Refresh command) {
        RotatedTokens rotated = rotator.rotate(command);
        if (!rotated.isRotated()) {
            throw new InvalidRefreshTokenException(
                    INVALID_REFRESH_TOKEN, rotated.outcome() == RefreshTokenRotationOutcome.REUSE_DETECTED);
        }
        AuthenticatedIdentity identity = identityResolver
                .resolve(rotated.user())
                .orElseThrow(() -> new InvalidRefreshTokenException(INVALID_REFRESH_TOKEN, false));
        return sessionIssuer
                .resume(identity, rotated.issued())
                .withDisplayName(rotated.user().displayName());
    }

    public void logout(String refreshToken) {
        if (!rotator.revokeSession(refreshToken)) {
            throw new InvalidRefreshTokenException(INVALID_REFRESH_TOKEN, false);
        }
    }

    /** 解析不出身分(停權或已無成員資格)一律走與密碼錯誤相同的失敗路徑,不揭露差異。 */
    private AuthSession startSession(User user, ClientInfo client) {
        AuthenticatedIdentity identity = identityResolver
                .resolve(user)
                .orElseThrow(() -> new AuthenticationFailedException(INVALID_CREDENTIALS));
        return sessionIssuer.issueNewSession(identity, client).withDisplayName(user.displayName());
    }
}
