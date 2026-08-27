package com.ctip.application.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ctip.application.rbac.RoleCode;
import com.ctip.domain.event.TenantEvents;
import com.ctip.domain.event.UserEvents;
import com.ctip.domain.user.RevokedReason;
import com.ctip.domain.user.TokenHash;
import java.time.Duration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * 認證流程(docs/spec/10-identity-plans.md §10.4)的 core 層行為:註冊、登入、鎖定、
 * 輪替與重用偵測、登出。以 in-memory port 執行,不啟動 Spring。
 */
@Tag("unit")
class AuthServiceTest {

    private static final String EMAIL = "analyst@example.org";

    @Test
    void registrationCreatesTenantEnrolsAdminAndPublishesEvents() {
        AuthServiceFixture fixture = new AuthServiceFixture();
        AuthSession session = fixture.register(EMAIL);

        assertThat(session.identity().role()).isEqualTo(RoleCode.TENANT_ADMIN);
        assertThat(session.identity().tenantId().isPublic()).isFalse();
        assertThat(session.accessToken()).isNotBlank();
        assertThat(session.refreshToken()).isNotBlank();
        assertThat(session.displayName()).isEqualTo("Unit Tester");
        assertThat(fixture.events.published())
                .hasAtLeastOneElementOfType(TenantEvents.TenantCreated.class)
                .hasAtLeastOneElementOfType(UserEvents.UserRegistered.class);
        assertThat(fixture.tenants.findById(session.identity().tenantId())).isPresent();
    }

    @Test
    void duplicateEmailIsRejected() {
        AuthServiceFixture fixture = new AuthServiceFixture();
        fixture.register(EMAIL);
        assertThatThrownBy(() -> fixture.register("Analyst@Example.ORG"))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
    }

    @Test
    void slugCollisionsGetAUniqueSuffix() {
        AuthServiceFixture fixture = new AuthServiceFixture();
        AuthSession first = fixture.register("a@example.org");
        AuthSession second = fixture.register("b@example.org");
        assertThat(fixture.tenants
                        .findById(first.identity().tenantId())
                        .orElseThrow()
                        .slug())
                .isNotEqualTo(fixture.tenants
                        .findById(second.identity().tenantId())
                        .orElseThrow()
                        .slug());
    }

    @Test
    void accessTokenClaimsCarryRoleAndPermissionsOnly() {
        AuthServiceFixture fixture = new AuthServiceFixture();
        AuthSession session = fixture.register(EMAIL);
        var claims = fixture.accessTokens.claimsOf(session.accessToken());
        assertThat(claims.roles()).containsExactly(RoleCode.TENANT_ADMIN.name());
        assertThat(claims.permissions()).isEqualTo(session.identity().permissions());
        assertThat(claims.userId()).isEqualTo(session.identity().userId());
    }

    @Test
    void loginSucceedsWithCorrectPasswordAndFailsOtherwise() {
        AuthServiceFixture fixture = new AuthServiceFixture();
        fixture.register(EMAIL);
        assertThat(fixture.login(EMAIL, AuthServiceFixture.PASSWORD).accessToken())
                .isNotBlank();
        assertThatThrownBy(() -> fixture.login(EMAIL, "wrong-password-value"))
                .isInstanceOf(AuthenticationFailedException.class);
        assertThatThrownBy(() -> fixture.login("nobody@example.org", AuthServiceFixture.PASSWORD))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    /** 不變量 U7:失敗計數必須留存(不隨失敗的交易 rollback),達門檻後鎖定並於期滿解除。 */
    @Test
    void repeatedFailuresLockTheAccountUntilTheLockExpires() {
        AuthServiceFixture fixture = new AuthServiceFixture();
        fixture.register(EMAIL);
        for (int attempt = 0; attempt < 10; attempt++) {
            assertThatThrownBy(() -> fixture.login(EMAIL, "wrong-password-value"))
                    .isInstanceOf(AuthenticationFailedException.class);
        }
        assertThatThrownBy(() -> fixture.login(EMAIL, AuthServiceFixture.PASSWORD))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessageContaining("locked");

        fixture.advance(Duration.ofMinutes(16));
        assertThat(fixture.login(EMAIL, AuthServiceFixture.PASSWORD).accessToken())
                .isNotBlank();
        // 成功登入後計數歸零
        assertThat(fixture.users
                        .findByEmail(new com.ctip.domain.user.EmailAddress(EMAIL))
                        .orElseThrow()
                        .failedLoginCount())
                .isZero();
    }

    @Test
    void rotationConsumesTheOldTokenAndKeepsTheFamily() {
        AuthServiceFixture fixture = new AuthServiceFixture();
        AuthSession first = fixture.register(EMAIL);
        AuthSession second = fixture.refresh(first.refreshToken());

        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
        var consumed = fixture.refreshTokens
                .findByHash(TokenHash.of(first.refreshToken()))
                .orElseThrow();
        assertThat(consumed.isUsed()).isTrue();
        assertThat(consumed.revokedReason()).isEqualTo(RevokedReason.ROTATED);
    }

    @Test
    void reuseRevokesTheFamilyAndPublishesTokenReuseDetected() {
        AuthServiceFixture fixture = new AuthServiceFixture();
        AuthSession first = fixture.register(EMAIL);
        AuthSession second = fixture.refresh(first.refreshToken());

        assertThatThrownBy(() -> fixture.refresh(first.refreshToken()))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .satisfies(e -> assertThat(((InvalidRefreshTokenException) e).isReuseDetected())
                        .isTrue());
        assertThat(fixture.events.published()).hasAtLeastOneElementOfType(UserEvents.TokenReuseDetected.class);
        assertThatThrownBy(() -> fixture.refresh(second.refreshToken()))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void unknownAndBlankRefreshTokensAreRejected() {
        AuthServiceFixture fixture = new AuthServiceFixture();
        assertThatThrownBy(() -> fixture.refresh("no-such-token")).isInstanceOf(InvalidRefreshTokenException.class);
        assertThatThrownBy(() -> fixture.refresh("  ")).isInstanceOf(InvalidRefreshTokenException.class);
        assertThatThrownBy(() -> fixture.authService.logout("no-such-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void logoutRevokesTheWholeFamily() {
        AuthServiceFixture fixture = new AuthServiceFixture();
        AuthSession first = fixture.register(EMAIL);
        AuthSession second = fixture.refresh(first.refreshToken());
        fixture.authService.logout(second.refreshToken());

        assertThat(fixture.refreshTokens.findByFamily(fixture.refreshTokens
                        .findByHash(TokenHash.of(second.refreshToken()))
                        .orElseThrow()
                        .familyId()))
                .allSatisfy(token -> assertThat(token.isRevoked()).isTrue());
        assertThatThrownBy(() -> fixture.refresh(second.refreshToken()))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }
}
