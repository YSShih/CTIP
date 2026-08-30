package com.ctip.application.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.user.EmailAddress;
import com.ctip.domain.user.RefreshToken;
import com.ctip.domain.user.RefreshTokenId;
import com.ctip.domain.user.RefreshTokenSnapshot;
import com.ctip.domain.user.TokenFamilyId;
import com.ctip.domain.user.TokenHash;
import com.ctip.domain.user.User;
import com.ctip.domain.user.UserId;
import com.ctip.domain.user.UserSnapshot;
import com.ctip.domain.user.UserStatus;
import com.ctip.testing.FakePasswordHasher;
import com.ctip.testing.FixedClockPort;
import com.ctip.testing.InMemoryRefreshTokenRepository;
import com.ctip.testing.InMemoryUserRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * 變更密碼(ADR 0015 指定的 M3 責任):改完必須<strong>撤銷該使用者全部 token family</strong>,
 * 否則已握有 refresh token 的攻擊者不受影響——那正是使用者改密碼要防的事。
 */
@Tag("unit")
class PasswordChangeServiceTest {

    private static final UserId USER_ID = new UserId(UUID.fromString("66666666-6666-6666-6666-666666666666"));
    private static final TenantId TENANT = new TenantId(UUID.fromString("77777777-7777-7777-7777-777777777777"));
    private static final String CURRENT = "current-password-1234";
    private static final String REPLACEMENT = "replacement-password-1234";

    private InMemoryUserRepository users;
    private InMemoryRefreshTokenRepository tokens;
    private FakePasswordHasher hasher;
    private PasswordChangeService service;

    @BeforeEach
    void setUp() {
        users = new InMemoryUserRepository();
        tokens = new InMemoryRefreshTokenRepository();
        hasher = new FakePasswordHasher();
        users.save(User.reconstitute(new UserSnapshot(
                USER_ID,
                EmailAddress.of("changer@example.org"),
                hasher.hash(CURRENT),
                "Changer",
                UserStatus.ACTIVE,
                TENANT,
                null,
                0,
                null)));
        service = new PasswordChangeService(users, hasher, tokens, FixedClockPort.at(FixedClockPort.DEFAULT_NOW));
    }

    @Test
    void theNewPasswordReplacesTheOldOne() {
        service.change(USER_ID, CURRENT, REPLACEMENT);

        User updated = users.findById(USER_ID).orElseThrow();
        assertThat(hasher.matches(REPLACEMENT, updated.passwordHash())).isTrue();
        assertThat(hasher.matches(CURRENT, updated.passwordHash())).isFalse();
    }

    @Test
    void everyRefreshTokenOfThatUserIsRevoked() {
        tokens.save(activeToken("family-one"));
        tokens.save(activeToken("family-two"));

        int revoked = service.change(USER_ID, CURRENT, REPLACEMENT);

        assertThat(revoked).isEqualTo(2);
        assertThat(tokens.findActiveByUser(USER_ID)).isEmpty();
    }

    /** 兩個不同 family 的存活 token:改密碼必須把兩個都撤掉,而不只是當下這一個。 */
    private static RefreshToken activeToken(String family) {
        return RefreshToken.issue(new RefreshTokenSnapshot(
                new RefreshTokenId(UUID.nameUUIDFromBytes(family.getBytes(java.nio.charset.StandardCharsets.UTF_8))),
                USER_ID,
                TokenHash.of(family + "-plaintext"),
                new TokenFamilyId(
                        UUID.nameUUIDFromBytes(("f-" + family).getBytes(java.nio.charset.StandardCharsets.UTF_8))),
                null,
                FixedClockPort.DEFAULT_NOW,
                FixedClockPort.DEFAULT_NOW.plus(java.time.Duration.ofDays(30)),
                null,
                null,
                null,
                "junit-agent",
                "203.0.113.7"));
    }

    @Test
    void theWrongCurrentPasswordIsRejectedAndNothingChanges() {
        assertThatThrownBy(() -> service.change(USER_ID, "not-the-password", REPLACEMENT))
                .isInstanceOf(AuthenticationFailedException.class);

        assertThat(hasher.matches(CURRENT, users.findById(USER_ID).orElseThrow().passwordHash()))
                .isTrue();
    }

    @Test
    void aNewPasswordThatViolatesThePolicyIsRejected() {
        assertThatThrownBy(() -> service.change(USER_ID, CURRENT, "short"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anUnknownUserFailsTheSameWayAsAWrongPassword() {
        assertThatThrownBy(() -> service.change(new UserId(UUID.randomUUID()), CURRENT, REPLACEMENT))
                .isInstanceOf(AuthenticationFailedException.class);
    }
}
