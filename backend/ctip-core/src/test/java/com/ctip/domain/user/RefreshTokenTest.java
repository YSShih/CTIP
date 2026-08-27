package com.ctip.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ctip.domain.tenant.TenantId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** RefreshToken 內部實體與 User 的邊界行為(不變量 U4–U6 的細節分支)。 */
@Tag("unit")
class RefreshTokenTest {

    private static final Instant NOW = Instant.parse("2026-08-27T08:00:00Z");
    private static final UserId USER = new UserId(new UUID(0, 2));
    private static final TokenFamilyId FAMILY = new TokenFamilyId(new UUID(0, 3));

    private static RefreshTokenSnapshot snapshot(RefreshTokenId id, String plaintext, RefreshTokenId parent) {
        return new RefreshTokenSnapshot(
                id,
                USER,
                TokenHash.of(plaintext),
                FAMILY,
                parent,
                NOW,
                NOW.plus(Duration.ofDays(30)),
                null,
                null,
                null,
                "junit-agent",
                "203.0.113.7");
    }

    private static RefreshToken issue(String plaintext) {
        return RefreshToken.issue(snapshot(new RefreshTokenId(new UUID(0, 4)), plaintext, null));
    }

    @Test
    void snapshotFieldsSurviveReconstitution() {
        RefreshToken token = RefreshToken.reconstitute(
                snapshot(new RefreshTokenId(new UUID(0, 5)), "plaintext-a", new RefreshTokenId(new UUID(0, 4))));
        assertThat(token.userId()).isEqualTo(USER);
        assertThat(token.familyId()).isEqualTo(FAMILY);
        assertThat(token.parentId()).isEqualTo(new RefreshTokenId(new UUID(0, 4)));
        assertThat(token.issuedAt()).isEqualTo(NOW);
        assertThat(token.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));
        assertThat(token.usedAt()).isNull();
        assertThat(token.revokedAt()).isNull();
        assertThat(token.userAgent()).isEqualTo("junit-agent");
        assertThat(token.ip()).isEqualTo("203.0.113.7");
        assertThat(token.isUsable(NOW)).isTrue();
        assertThat(token.isUsable(NOW.plus(Duration.ofDays(31)))).isFalse();
    }

    @Test
    void issueRejectsAlreadyConsumedOrRevokedSnapshots() {
        RefreshTokenSnapshot base = snapshot(new RefreshTokenId(new UUID(0, 6)), "plaintext-b", null);
        RefreshTokenSnapshot used = new RefreshTokenSnapshot(
                base.id(),
                base.userId(),
                base.tokenHash(),
                base.familyId(),
                null,
                base.issuedAt(),
                base.expiresAt(),
                NOW,
                null,
                null,
                null,
                null);
        assertThatThrownBy(() -> RefreshToken.issue(used)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void expiryMustBeAfterIssueAndReasonRequiresRevocationTime() {
        RefreshTokenSnapshot inverted = new RefreshTokenSnapshot(
                new RefreshTokenId(new UUID(0, 7)),
                USER,
                TokenHash.of("x"),
                FAMILY,
                null,
                NOW,
                NOW,
                null,
                null,
                null,
                null,
                null);
        assertThatThrownBy(() -> RefreshToken.reconstitute(inverted)).isInstanceOf(IllegalArgumentException.class);

        RefreshTokenSnapshot danglingReason = new RefreshTokenSnapshot(
                new RefreshTokenId(new UUID(0, 8)),
                USER,
                TokenHash.of("y"),
                FAMILY,
                null,
                NOW,
                NOW.plusSeconds(60),
                null,
                null,
                RevokedReason.ADMIN,
                null,
                null);
        assertThatThrownBy(() -> RefreshToken.reconstitute(danglingReason))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tokenHashRejectsAnythingButLowercaseHex() {
        assertThatThrownBy(() -> new TokenHash("NOT-HEX")).isInstanceOf(IllegalArgumentException.class);
        assertThat(TokenHash.of("plaintext").value()).hasSize(64);
    }

    @Test
    void rotationRejectsTokensOfAnotherUserOrAnotherFamily() {
        User user = user();
        RefreshToken foreign = RefreshToken.issue(new RefreshTokenSnapshot(
                new RefreshTokenId(new UUID(0, 9)),
                new UserId(new UUID(0, 99)),
                TokenHash.of("foreign"),
                FAMILY,
                null,
                NOW,
                NOW.plusSeconds(60),
                null,
                null,
                null,
                null,
                null));
        RefreshTokenRotationCommand command = new RefreshTokenRotationCommand(foreign, List.of(foreign), foreign, NOW);
        assertThatThrownBy(() -> user.rotateRefreshToken(command)).isInstanceOf(IllegalArgumentException.class);

        RefreshToken presented = issue("presented");
        RefreshToken otherFamily = RefreshToken.issue(new RefreshTokenSnapshot(
                new RefreshTokenId(new UUID(0, 10)),
                USER,
                TokenHash.of("other-family"),
                new TokenFamilyId(new UUID(0, 77)),
                null,
                NOW,
                NOW.plusSeconds(60),
                null,
                null,
                null,
                null,
                null));
        RefreshTokenRotationCommand mismatched =
                new RefreshTokenRotationCommand(presented, List.of(presented), otherFamily, NOW);
        assertThatThrownBy(() -> user.rotateRefreshToken(mismatched)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void revokedTokenIsRejectedWithoutRevokingTheFamily() {
        User user = user();
        RefreshToken presented = issue("revoked-token");
        presented.revoke(NOW, RevokedReason.ADMIN);
        RefreshToken replacement =
                RefreshToken.issue(snapshot(new RefreshTokenId(new UUID(0, 11)), "replacement", presented.id()));

        RefreshTokenRotation rotation = user.rotateRefreshToken(
                new RefreshTokenRotationCommand(presented, List.of(presented), replacement, NOW));
        assertThat(rotation.outcome()).isEqualTo(RefreshTokenRotationOutcome.INVALID);
        assertThat(rotation.mutated()).isEmpty();
        // 重複撤銷保留最初原因(K6 的姊妹規則)
        presented.revoke(NOW.plusSeconds(60), RevokedReason.LOGOUT);
        assertThat(presented.revokedReason()).isEqualTo(RevokedReason.ADMIN);
    }

    @Test
    void userProfileMutatorsAndStatus() {
        User user = user();
        assertThat(user.status()).isEqualTo(UserStatus.ACTIVE);
        user.rename("Renamed");
        assertThat(user.displayName()).isEqualTo("Renamed");
        user.suspend();
        assertThat(user.isActive()).isFalse();
    }

    private static User user() {
        return User.reconstitute(new UserSnapshot(
                USER,
                new EmailAddress("rt@example.org"),
                new PasswordHash("$2a$12$abcdefghijklmnopqrstuuKq1F9L1hE8OJ0hFm8dxOFVXzB4rNKa"),
                "RT Tester",
                UserStatus.ACTIVE,
                new TenantId(new UUID(0, 1)),
                null,
                0,
                null));
    }
}
