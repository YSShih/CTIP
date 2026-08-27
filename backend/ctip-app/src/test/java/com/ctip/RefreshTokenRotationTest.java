package com.ctip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ctip.application.identity.AuthCommands;
import com.ctip.application.identity.AuthService;
import com.ctip.application.identity.AuthSession;
import com.ctip.application.identity.InvalidRefreshTokenException;
import com.ctip.application.port.RefreshTokenRepository;
import com.ctip.application.port.TenantMembershipRepository;
import com.ctip.application.rbac.RoleCode;
import com.ctip.domain.user.RefreshToken;
import com.ctip.domain.user.RevokedReason;
import com.ctip.domain.user.TokenHash;
import com.ctip.support.TestIdentities;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * DoD M2-03:refresh token 輪替與重用偵測(不變量 U4–U6;docs/spec/10-identity-plans.md §10.4)。
 * 逐條驗:U4 單次使用、U5 重用觸發 family 全撤、U6 同一 family 最多一枚可用。
 */
class RefreshTokenRotationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private RefreshTokenRepository refreshTokens;

    @Autowired
    private TenantMembershipRepository memberships;

    private TestIdentities identities;

    @BeforeEach
    void setUp() {
        identities = new TestIdentities(authService, memberships);
    }

    private AuthSession session(String email) {
        return identities.register(email, RoleCode.TENANT_ADMIN);
    }

    private AuthSession rotate(String refreshToken) {
        return authService.refresh(new AuthCommands.Refresh(refreshToken, "junit", "127.0.0.1"));
    }

    private RefreshToken stored(String plaintext) {
        return refreshTokens.findByHash(TokenHash.of(plaintext)).orElseThrow();
    }

    @Test
    void u4RotationConsumesThePresentedTokenAndIssuesANewOne() {
        AuthSession first = session("rotate-u4@example.org");
        AuthSession second = rotate(first.refreshToken());

        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
        RefreshToken consumed = stored(first.refreshToken());
        assertThat(consumed.isUsed()).isTrue();
        assertThat(consumed.revokedReason()).isEqualTo(RevokedReason.ROTATED);

        RefreshToken issued = stored(second.refreshToken());
        assertThat(issued.familyId()).isEqualTo(consumed.familyId());
        assertThat(issued.parentId()).isEqualTo(consumed.id());
    }

    @Test
    void u5ReuseOfAConsumedTokenRevokesTheWholeFamily() {
        AuthSession first = session("rotate-u5@example.org");
        AuthSession second = rotate(first.refreshToken());
        AuthSession third = rotate(second.refreshToken());

        // 重放第一枚(已使用)→ 拒絕,且整個 family 立即失效
        assertThatThrownBy(() -> rotate(first.refreshToken()))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .satisfies(e -> assertThat(((InvalidRefreshTokenException) e).isReuseDetected())
                        .isTrue());

        List<RefreshToken> family =
                refreshTokens.findByFamily(stored(first.refreshToken()).familyId());
        assertThat(family)
                .hasSize(3)
                .allSatisfy(token -> assertThat(token.isRevoked()).isTrue());
        assertThat(stored(third.refreshToken()).revokedReason()).isEqualTo(RevokedReason.REUSE_DETECTED);

        // 撤銷後連當時仍有效的最新一枚也不能再用
        assertThatThrownBy(() -> rotate(third.refreshToken())).isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void u6AtMostOneUsableTokenPerFamily() {
        AuthSession first = session("rotate-u6@example.org");
        AuthSession second = rotate(first.refreshToken());
        AuthSession third = rotate(second.refreshToken());

        List<RefreshToken> family =
                refreshTokens.findByFamily(stored(third.refreshToken()).familyId());
        java.time.Instant now = java.time.Instant.now();
        assertThat(family.stream().filter(token -> token.isUsable(now)).toList())
                .singleElement()
                .satisfies(token -> assertThat(token.tokenHash()).isEqualTo(TokenHash.of(third.refreshToken())));
    }

    @Test
    void unknownTokenIsRejectedWithoutTouchingAnyFamily() {
        assertThatThrownBy(() -> rotate("not-a-real-refresh-token"))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .satisfies(e -> assertThat(((InvalidRefreshTokenException) e).isReuseDetected())
                        .isFalse());
    }

    @Test
    void logoutRevokesEveryTokenInTheFamily() {
        AuthSession first = session("rotate-logout@example.org");
        AuthSession second = rotate(first.refreshToken());
        authService.logout(second.refreshToken());

        assertThat(refreshTokens.findByFamily(stored(second.refreshToken()).familyId()))
                .allSatisfy(token -> assertThat(token.isRevoked()).isTrue());
        assertThat(stored(second.refreshToken()).revokedReason()).isEqualTo(RevokedReason.LOGOUT);
        assertThatThrownBy(() -> rotate(second.refreshToken())).isInstanceOf(InvalidRefreshTokenException.class);
    }
}
