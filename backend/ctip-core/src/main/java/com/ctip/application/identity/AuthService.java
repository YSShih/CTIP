package com.ctip.application.identity;

import com.ctip.domain.user.RefreshTokenRotationOutcome;
import com.ctip.domain.user.User;
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

    public AuthSession login(AuthCommands.Login command) {
        LoginResult result = loginAuthenticator.authenticate(command);
        if (!result.isSuccess()) {
            throw new AuthenticationFailedException(
                    result.failure() == LoginFailure.LOCKED ? "Account temporarily locked" : INVALID_CREDENTIALS);
        }
        return startSession(result.user(), new ClientInfo(command.userAgent(), command.ip()));
    }

    public AuthSession refresh(AuthCommands.Refresh command) {
        RotatedTokens rotated = rotator.rotate(command);
        if (!rotated.isRotated()) {
            throw new InvalidRefreshTokenException(
                    INVALID_REFRESH_TOKEN, rotated.outcome() == RefreshTokenRotationOutcome.REUSE_DETECTED);
        }
        return sessionIssuer
                .complete(identityResolver.resolve(rotated.user()), rotated.issued())
                .withDisplayName(rotated.user().displayName());
    }

    public void logout(String refreshToken) {
        if (!rotator.revokeSession(refreshToken)) {
            throw new InvalidRefreshTokenException(INVALID_REFRESH_TOKEN, false);
        }
    }

    private AuthSession startSession(User user, ClientInfo client) {
        return sessionIssuer
                .issueNewSession(identityResolver.resolve(user), client)
                .withDisplayName(user.displayName());
    }
}
